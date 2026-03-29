/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.nowarn
import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration.DurationInt
import scala.util.Success
import scala.util.control.NoStackTrace

import org.apache.pekko.Done
import org.apache.pekko.stream.{ AbruptStageTerminationException, ActorAttributes, ActorMaterializer, ClosedShape, Supervision }
import org.apache.pekko.stream.testkit.{ StreamSpec, TestSubscriber }
import org.apache.pekko.stream.testkit.Utils.TE
import org.apache.pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import org.apache.pekko.testkit.EventFilter

class FlowGatherSpec extends StreamSpec {

  private val ex = new Exception("TEST") with NoStackTrace

  object BeenCalledTimesGate {
    def apply(): BeenCalledTimesGate = new BeenCalledTimesGate(1)
    def apply(nTimes: Int): BeenCalledTimesGate = new BeenCalledTimesGate(nTimes)
  }

  class BeenCalledTimesGate(nTimes: Int) {
    private val beenCalled = new AtomicInteger(0)

    def mark(): Unit = beenCalled.updateAndGet { current =>
      if (current == nTimes)
        throw new IllegalStateException(s"Has been called:[$nTimes] times, should not be called anymore.")
      else current + 1
    }

    def ensure(): Unit =
      if (beenCalled.get() != nTimes)
        throw new IllegalStateException(s"Expected to be called:[$nTimes], but only be called:[$beenCalled]")
  }

  "A Gather" must {
    "work in the happy case" in {
      val gate = BeenCalledTimesGate()
      Source(List(1, 2, 3, 4, 5))
        .gather(() =>
          new Gatherer[Int, (Int, Int)] {
            private var agg = 0

            override def apply(elem: Int, collector: GatherCollector[(Int, Int)]): Unit = {
              collector.push((agg, elem))
              agg += elem
            }

            override def onComplete(collector: GatherCollector[(Int, Int)]): Unit =
              gate.mark()
          })
        .runWith(TestSink[(Int, Int)]())
        .request(6)
        .expectNext((0, 1))
        .expectNext((1, 2))
        .expectNext((3, 3))
        .expectNext((6, 4))
        .expectNext((10, 5))
        .expectComplete()
      gate.ensure()
    }

    "remember state when complete" in {
      val gate = BeenCalledTimesGate()
      Source(1 to 10)
        .gather(() =>
          new Gatherer[Int, List[Int]] {
            private var state = List.empty[Int]

            override def apply(elem: Int, collector: GatherCollector[List[Int]]): Unit = {
              val newState = elem :: state
              if (newState.size == 3) {
                state = Nil
                collector.push(newState.reverse)
              } else
                state = newState
            }

            override def onComplete(collector: GatherCollector[List[Int]]): Unit = {
              gate.mark()
              collector.push(state.reverse)
            }
          })
        .mapConcat(identity)
        .runWith(TestSink[Int]())
        .request(10)
        .expectNextN(List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        .expectComplete()
      gate.ensure()
    }

    "emit zero or more elements and drain on completion" in {
      Source(1 to 5)
        .gather(() =>
          new Gatherer[Int, List[Int]] {
            private var buffer = List.empty[Int]

            override def apply(elem: Int, collector: GatherCollector[List[Int]]): Unit = {
              buffer = elem :: buffer
              if (buffer.size == 2) {
                collector.push(buffer.reverse)
                buffer = Nil
              }
            }

            override def onComplete(collector: GatherCollector[List[Int]]): Unit =
              if (buffer.nonEmpty)
                collector.push(buffer.reverse)
          })
        .runWith(TestSink[List[Int]]())
        .request(3)
        .expectNext(List(1, 2))
        .expectNext(List(3, 4))
        .expectNext(List(5))
        .expectComplete()
    }

    "emit all outputs when a callback deopts from single to multi mode" in {
      Source.single(1)
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit = {
              collector.push(elem)
              collector.push(elem + 1)
            }
          })
        .runWith(TestSink[Int]())
        .request(2)
        .expectNext(1, 2)
        .expectComplete()
    }

    "drop a single buffered output if apply throws after pushing it" in {
      Source.single(1)
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit = {
              collector.push(elem)
              throw TE("boom")
            }
          })
        .runWith(TestSink[Int]())
        .request(1)
        .expectError(TE("boom"))
    }

    "resume without leaking a single buffered output if apply throws after pushing it" in {
      Source(List(1, 2))
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit = {
              collector.push(elem)
              if (elem == 1)
                throw ex
            }
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(TestSink[Int]())
        .request(1)
        .expectNext(2)
        .expectComplete()
    }

    "be usable as zipWithIndex" in {
      val gate = BeenCalledTimesGate()
      Source(List("A", "B", "C", "D"))
        .gather(() =>
          new Gatherer[String, (String, Long)] {
            private var index = 0L

            override def apply(elem: String, collector: GatherCollector[(String, Long)]): Unit = {
              collector.push((elem, index))
              index += 1
            }

            override def onComplete(collector: GatherCollector[(String, Long)]): Unit =
              gate.mark()
          })
        .runWith(TestSink[(String, Long)]())
        .request(4)
        .expectNext(("A", 0L))
        .expectNext(("B", 1L))
        .expectNext(("C", 2L))
        .expectNext(("D", 3L))
        .expectComplete()
      gate.ensure()
    }

    "respect backpressure for public single-output gatherers" in {
      Source(List("A", "B", "C"))
        .gather(() =>
          new Gatherer[String, String] {
            override def apply(elem: String, collector: GatherCollector[String]): Unit =
              collector.push(elem)
          })
        .runWith(TestSink[String]())
        .request(1)
        .expectNext("A")
        .expectNoMessage(200.millis)
        .request(1)
        .expectNext("B")
        .request(1)
        .expectNext("C")
        .expectComplete()
    }

    "respect backpressure for one-to-one gatherers" in {
      Source(List("A", "B", "C"))
        .gather(() =>
          new OneToOneGatherer[String, String] {
            override def applyOne(elem: String): String = elem
          })
        .runWith(TestSink[String]())
        .request(1)
        .expectNext("A")
        .expectNoMessage(200.millis)
        .request(1)
        .expectNext("B")
        .request(1)
        .expectNext("C")
        .expectComplete()
    }

    "be usable as bufferUntilChanged" in {
      val gate = BeenCalledTimesGate()
      Source("A" :: "B" :: "B" :: "C" :: "C" :: "C" :: "D" :: Nil)
        .gather(() =>
          new Gatherer[String, List[String]] {
            private var buffer = List.empty[String]

            override def apply(elem: String, collector: GatherCollector[List[String]]): Unit =
              buffer match {
                case head :: _ if head != elem =>
                  collector.push(buffer.reverse)
                  buffer = elem :: Nil
                case _ =>
                  buffer = elem :: buffer
              }

            override def onComplete(collector: GatherCollector[List[String]]): Unit = {
              gate.mark()
              if (buffer.nonEmpty)
                collector.push(buffer.reverse)
            }
          })
        .runWith(TestSink[List[String]]())
        .request(4)
        .expectNext(List("A"))
        .expectNext(List("B", "B"))
        .expectNext(List("C", "C", "C"))
        .expectNext(List("D"))
        .expectComplete()
      gate.ensure()
    }

    "be usable as distinctUntilChanged" in {
      val gate = BeenCalledTimesGate()
      Source("A" :: "B" :: "B" :: "C" :: "C" :: "C" :: "D" :: Nil)
        .gather(() =>
          new Gatherer[String, String] {
            private var lastElement: Option[String] = None

            override def apply(elem: String, collector: GatherCollector[String]): Unit =
              lastElement match {
                case Some(last) if last == elem =>
                  lastElement = Some(elem)
                case _ =>
                  lastElement = Some(elem)
                  collector.push(elem)
              }

            override def onComplete(collector: GatherCollector[String]): Unit =
              gate.mark()
          })
        .runWith(TestSink[String]())
        .request(4)
        .expectNext("A")
        .expectNext("B")
        .expectNext("C")
        .expectNext("D")
        .expectComplete()
      gate.ensure()
    }

    "resume when supervision says Resume" in {
      Source(List(1, 2, 3, 4, 5))
        .gather(() =>
          new Gatherer[Int, Int] {
            private var sum = 0

            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              if (elem % 2 == 0)
                throw ex
              else {
                sum += elem
                collector.push(sum)
              }
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(TestSink[Int]())
        .request(3)
        .expectNext(1, 4, 9)
        .expectComplete()
    }

    "emit onComplete elements before restarting" in {
      val generation = new AtomicInteger(0)
      val (source, sink) = TestSource[String]()
        .viaMat(Flow[String].gather(() => {
            val currentGeneration = generation.incrementAndGet()
            new Gatherer[String, String] {
              override def apply(elem: String, collector: GatherCollector[String]): Unit =
                if (elem == "boom") throw TE("boom")
                else collector.push(s"$elem$currentGeneration")

              override def onComplete(collector: GatherCollector[String]): Unit =
                collector.push(s"onClose$currentGeneration")
            }
          }))(Keep.left)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .toMat(TestSink())(Keep.both)
        .run()

      sink.request(1)
      source.sendNext("one")
      sink.expectNext("one1")
      sink.request(1)
      source.sendNext("boom")
      sink.expectNext("onClose1")
      sink.request(1)
      source.sendNext("two")
      sink.expectNext("two2")
      sink.cancel()
      source.expectCancellation()
    }

    "restart and recreate gatherer state when supervision says Restart" in {
      val generation = new AtomicInteger(0)
      Source(List(1, 2, 3, 4, 5))
        .gather(() => {
          generation.incrementAndGet()
          new Gatherer[Int, (Int, Int)] {
            private var agg = 0

            override def apply(elem: Int, collector: GatherCollector[(Int, Int)]): Unit =
              if (elem % 3 == 0)
                throw ex
              else {
                collector.push((agg, elem))
                agg += elem
              }
          }
        })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .runWith(TestSink[(Int, Int)]())
        .request(5)
        .expectNext((0, 1))
        .expectNext((1, 2))
        .expectNext((0, 4))
        .expectNext((4, 5))
        .expectComplete()
      generation.get() shouldBe 2
    }

    "stop when supervision says Stop" in {
      val gate = BeenCalledTimesGate()
      Source(List(1, 2, 3, 4, 5))
        .gather(() =>
          new Gatherer[Int, (Int, Int)] {
            private var agg = 0

            override def apply(elem: Int, collector: GatherCollector[(Int, Int)]): Unit =
              if (elem % 3 == 0)
                throw ex
              else {
                collector.push((agg, elem))
                agg += elem
              }

            override def onComplete(collector: GatherCollector[(Int, Int)]): Unit =
              gate.mark()
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .runWith(TestSink[(Int, Int)]())
        .request(5)
        .expectNext((0, 1))
        .expectNext((1, 2))
        .expectError(ex)
      gate.ensure()
    }

    "fail on upstream failure when onComplete emits nothing" in {
      val gate = BeenCalledTimesGate()
      val (source, sink) = TestSource[Int]()
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              collector.push(elem)

            override def onComplete(collector: GatherCollector[Int]): Unit =
              gate.mark()
          })
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      sink.request(3)
      source.sendNext(1)
      sink.expectNext(1)
      source.sendNext(2)
      sink.expectNext(2)
      source.sendError(ex)
      sink.expectError(ex)
      gate.ensure()
    }

    "defer upstream failure until onComplete elements are emitted" in {
      val (source, sink) = TestSource[Int]()
        .gather(() =>
          new Gatherer[Int, Int] {
            private var sum = 0

            override def apply(elem: Int, collector: GatherCollector[Int]): Unit = {
              sum += elem
              collector.push(sum)
            }

            override def onComplete(collector: GatherCollector[Int]): Unit =
              collector.push(-1)
          })
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      sink.request(3)
      source.sendNext(1)
      sink.expectNext(1)
      source.sendNext(2)
      sink.expectNext(3)
      source.sendError(ex)
      sink.expectNext(-1)
      sink.expectError(ex)
    }

    "emit buffered elements before failing when supervision stops the stage" in {
      Source(List(1, 2, 3))
        .gather(() =>
          new Gatherer[Int, List[Int]] {
            private var buffer = List.empty[Int]

            override def apply(elem: Int, collector: GatherCollector[List[Int]]): Unit =
              if (elem == 3)
                throw ex
              else
                buffer = elem :: buffer

            override def onComplete(collector: GatherCollector[List[Int]]): Unit =
              if (buffer.nonEmpty)
                collector.push(buffer.reverse)
          })
        .runWith(TestSink[List[Int]]())
        .request(2)
        .expectNext(List(1, 2))
        .expectError(ex)
    }

    "call onComplete when supervision stops the stage" in {
      val gate = BeenCalledTimesGate()
      val promise = Promise[Done]()
      val done = Source
        .single(1)
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              throw ex

            override def onComplete(collector: GatherCollector[Int]): Unit = {
              gate.mark()
              promise.complete(Success(Done))
            }
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .runWith(Sink.ignore)

      done.failed.futureValue shouldBe ex
      Await.result(promise.future, 3.seconds) shouldBe Done
      gate.ensure()
    }

    "cancel upstream when downstream cancels" in {
      val gate = BeenCalledTimesGate()
      val promise = Promise[Done]()
      val source = TestSource[Int]()
        .via(Flow[Int].gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              collector.push(elem)

            override def onComplete(collector: GatherCollector[Int]): Unit = {
              gate.mark()
              promise.complete(Success(Done))
            }
          }))
        .toMat(Sink.cancelled)(Keep.left)
        .run()

      source.expectCancellation()
      Await.result(promise.future, 3.seconds) shouldBe Done
      gate.ensure()
    }

    "cancel upstream when downstream fails" in {
      val gate = BeenCalledTimesGate()
      val promise = Promise[Done]()
      val testProbe = TestSubscriber.probe[Int]()
      val source = TestSource[Int]()
        .via(Flow[Int].gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              collector.push(elem)

            override def onComplete(collector: GatherCollector[Int]): Unit = {
              gate.mark()
              promise.complete(Success(Done))
            }
          }))
        .toMat(Sink.fromSubscriber(testProbe))(Keep.left)
        .run()

      testProbe.cancel(ex)
      source.expectCancellationWithCause(ex)
      Await.result(promise.future, 3.seconds) shouldBe Done
      gate.ensure()
    }

    "invoke onComplete exactly once when downstream cancels while draining final elements" in {
      val closedCounter = new AtomicInteger(0)
      val (source, sink) = TestSource[Int]()
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              collector.push(elem)

            override def onComplete(collector: GatherCollector[Int]): Unit = {
              closedCounter.incrementAndGet()
              collector.push(100)
              collector.push(200)
            }
          })
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      sink.request(2)
      source.sendNext(1)
      sink.expectNext(1)
      source.sendComplete()
      sink.expectNext(100)
      sink.cancel()
      closedCounter.get() shouldBe 1
    }

    "not restart gatherer when downstream cancels while draining restart elements" in {
      val generation = new AtomicInteger(0)
      val (source, sink) = TestSource[Int]()
        .gather(() => {
          val currentGeneration = generation.incrementAndGet()
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              throw TE(s"boom-$currentGeneration")

            override def onComplete(collector: GatherCollector[Int]): Unit = {
              collector.push(100 + currentGeneration)
              collector.push(200 + currentGeneration)
            }
          }
        })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      sink.request(1)
      source.sendNext(1)
      sink.expectNext(101)
      sink.cancel()
      generation.get() shouldBe 1
    }

    "fail when emitting null" in {
      Source.single(1)
        .gather(() =>
          new Gatherer[Int, String] {
            override def apply(elem: Int, collector: GatherCollector[String]): Unit =
              collector.push(null.asInstanceOf[String])
          })
        .runWith(TestSink[String]())
        .request(1)
        .expectError() shouldBe a[NullPointerException]
    }

    "call onComplete on abrupt materializer termination" in {
      val gate = BeenCalledTimesGate()
      val promise = Promise[Done]()
      @nowarn("msg=deprecated")
      val mat = ActorMaterializer()

      val matVal = Source
        .single(1)
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              collector.push(elem)

            override def onComplete(collector: GatherCollector[Int]): Unit = {
              gate.mark()
              promise.complete(Success(Done))
            }
          })
        .runWith(Sink.never)(mat)

      mat.shutdown()
      matVal.failed.futureValue shouldBe a[AbruptStageTerminationException]
      Await.result(promise.future, 3.seconds) shouldBe Done
      gate.ensure()
    }

    "will not call onComplete twice if apply fails" in {
      val closedCounter = new AtomicInteger(0)
      val probe = Source
        .repeat(1)
        .gather(() =>
          new Gatherer[Int, String] {
            override def apply(elem: Int, collector: GatherCollector[String]): Unit =
              throw TE("failing read")

            override def onComplete(collector: GatherCollector[String]): Unit =
              closedCounter.incrementAndGet()
          })
        .runWith(TestSink[String]())

      probe.request(1)
      probe.expectError(TE("failing read"))
      closedCounter.get() shouldBe 1
    }

    "will not call onComplete twice if both apply and onComplete fail" in {
      val closedCounter = new AtomicInteger(0)
      val probe = Source
        .repeat(1)
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              throw TE("failing read")

            override def onComplete(collector: GatherCollector[Int]): Unit =
              if (closedCounter.incrementAndGet() == 1)
                throw TE("boom")
          })
        .runWith(TestSink[Int]())

      EventFilter[TE](occurrences = 1).intercept {
        probe.request(1)
        probe.expectError(TE("boom"))
      }
      closedCounter.get() shouldBe 1
    }

    "will not call onComplete twice on cancel when onComplete fails" in {
      val closedCounter = new AtomicInteger(0)
      val (source, sink) = TestSource[Int]()
        .viaMat(Flow[Int].gather(() =>
            new Gatherer[Int, Int] {
              override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
                collector.push(elem)

              override def onComplete(collector: GatherCollector[Int]): Unit = {
                closedCounter.incrementAndGet()
                throw TE("boom")
              }
            }))(Keep.left)
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      EventFilter[TE](occurrences = 1).intercept {
        sink.request(1)
        source.sendNext(1)
        sink.expectNext(1)
        sink.cancel()
        source.expectCancellation()
      }
      closedCounter.get() shouldBe 1
    }

    "will not call onComplete twice if onComplete fails on upstream complete" in {
      val closedCounter = new AtomicInteger(0)
      val (source, sink) = TestSource[Int]()
        .gather(() =>
          new Gatherer[Int, Int] {
            override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
              collector.push(elem)

            override def onComplete(collector: GatherCollector[Int]): Unit = {
              closedCounter.incrementAndGet()
              throw TE("boom")
            }
          })
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      EventFilter[TE](occurrences = 1).intercept {
        sink.request(1)
        source.sendNext(1)
        sink.expectNext(1)
        sink.request(1)
        source.sendComplete()
        sink.expectError(TE("boom"))
      }
      closedCounter.get() shouldBe 1
    }
  }

  "support junction output ports" in {
    val source = Source(List((1, 1), (2, 2)))
    val graph = RunnableGraph.fromGraph(GraphDSL.createGraph(TestSink[(Int, Int)]()) { implicit b => sink =>
      import GraphDSL.Implicits._
      val unzip = b.add(Unzip[Int, Int]())
      val zip = b.add(Zip[Int, Int]())
      val gather = b.add(Flow[(Int, Int)].gather(() => (elem: (Int, Int), collector: GatherCollector[(Int, Int)]) => collector.push(elem)))

      source ~> unzip.in
      unzip.out0 ~> zip.in0
      unzip.out1 ~> zip.in1
      zip.out ~> gather ~> sink.in

      ClosedShape
    })

    graph
      .run()
      .request(2)
      .expectNext((1, 1))
      .expectNext((2, 2))
      .expectComplete()
  }
}
