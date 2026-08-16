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

import java.util.concurrent.{ ConcurrentLinkedQueue, CyclicBarrier }

import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.Done
import pekko.stream._
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler }
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSource

class SinkWatchTerminationSpec extends StreamSpec {

  "A Sink.watchTermination" must {

    "complete future with success when stream is completed" in {
      val done = Source(1 to 4).runWith(Sink.ignore.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "complete future with success when the stream is empty" in {
      val done = Source.empty[Int].runWith(Sink.ignore.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "complete future with success when the sink cancels itself" in {
      val done = Source(1 to 4).runWith(Sink.head[Int].watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "keep the original materialized value" in {
      val (head, done) = Source(1 to 4).runWith(Sink.head[Int].watchTermination(Keep.both))
      head.futureValue should ===(1)
      done.futureValue should ===(Done)
    }

    "keep materialized value transformations of the wrapped sink" in {
      val transformed: Sink[Int, scala.concurrent.Future[Int]] =
        Sink.headOption[Int].mapMaterializedValue(_.map(_.getOrElse(0))(ExecutionContext.parasitic))
      val (head, done) = Source(1 to 4).runWith(transformed.watchTermination(Keep.both))
      head.futureValue should ===(1)
      done.futureValue should ===(Done)
    }

    "fail future when stream is failed" in {
      val ex = new RuntimeException("Stream failed.") with NoStackTrace
      val (p, done) = TestSource[Int]().toMat(Sink.ignore.watchTermination(Keep.right))(Keep.both).run()
      p.sendNext(1)
      p.sendError(ex)
      whenReady(done.failed) { _ shouldBe ex }
    }

    "complete future only after the postStop of the wrapped sink has run" in {
      val events = new ConcurrentLinkedQueue[String]()

      class PostStopSignalingSink extends GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("PostStopSignalingSink.in")
        override val shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def postStop(): Unit = events.add("postStop")
            setHandler(in, this)
          }
      }

      val done = Source(1 to 4).runWith(Sink.fromGraph(new PostStopSignalingSink).watchTermination(Keep.right))
      done.onComplete(_ => events.add("futureCompleted"))(ExecutionContext.parasitic)
      done.futureValue should ===(Done)
      events.asScala.toList should ===(List("postStop", "futureCompleted"))
    }

    "fail future when stream abruptly terminated" in {
      val mat = Materializer(system)
      val done = TestSource[Int]().toMat(Sink.ignore.watchTermination(Keep.right))(Keep.both).run()(mat)._2
      mat.shutdown()
      done.failed.futureValue shouldBe an[AbruptTerminationException]
    }

    "complete future for a composite sink built with Sink.foreach" in {
      val done = Source(1 to 4).runWith(Sink.foreach[Int](_ => ()).watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "complete future for a composite sink built with Sink.combine" in {
      val combined = Sink.combine(Sink.ignore, Sink.ignore)(Broadcast[Int](_))
      val done = Source(1 to 4).runWith(combined.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "complete future for a composite sink built with GraphDSL" in {
      val composite = Sink.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._
        val bcast = b.add(Broadcast[Int](2))
        val s1 = b.add(Sink.ignore)
        val s2 = b.add(Sink.ignore)
        bcast.out(0) ~> s1
        bcast.out(1) ~> s2
        SinkShape(bcast.in)
      })
      val done = Source(1 to 4).runWith(composite.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "fail future when upstream fails on a composite sink" in {
      val ex = new RuntimeException("composite fail") with NoStackTrace
      val combined = Sink.combine(Sink.ignore, Sink.ignore)(Broadcast[Int](_))
      val (p, done) = TestSource[Int]().toMat(combined.watchTermination(Keep.right))(Keep.both).run()
      p.sendNext(1)
      p.sendError(ex)
      whenReady(done.failed) { _ shouldBe ex }
    }

    "complete future only after all stages' postStop have run in a composite sink" in {
      val events = new ConcurrentLinkedQueue[String]()

      class SignalingSink(name: String) extends GraphStage[SinkShape[Int]] {
        val in = Inlet[Int](s"$name.in")
        override val shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def postStop(): Unit = events.add(name)
            setHandler(in, this)
          }
      }

      val composite = Sink.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._
        val bcast = b.add(Broadcast[Int](2))
        val s1 = b.add(new SignalingSink("s1"))
        val s2 = b.add(new SignalingSink("s2"))
        bcast.out(0) ~> s1
        bcast.out(1) ~> s2
        SinkShape(bcast.in)
      })

      val done = Source(1 to 4).runWith(composite.watchTermination(Keep.right))
      done.onComplete(_ => events.add("futureCompleted"))(ExecutionContext.parasitic)
      done.futureValue should ===(Done)
      val list = events.asScala.toList
      list.last should ===("futureCompleted")
      list.filterNot(_ == "futureCompleted").toSet should ===(Set("s1", "s2"))
    }

    "keep the original materialized value of a composite sink" in {
      val composite = Sink.fromGraph(GraphDSL.createGraph(Sink.queue[Int]()) { implicit b => queue =>
        import GraphDSL.Implicits._
        val bcast = b.add(Broadcast[Int](2))
        bcast.out(0) ~> queue
        bcast.out(1) ~> Sink.ignore
        SinkShape(bcast.in)
      })
      val (queue, done) = Source(1 to 4).runWith(composite.watchTermination(Keep.both))
      queue.pull().futureValue should ===(Some(1))
      queue.cancel()
      done.futureValue should ===(Done)
    }

    "fail future when a fully fused composite stream abruptly terminated" in {
      val mat = Materializer(system)
      val combined = Sink.combine(Sink.ignore, Sink.ignore)(Broadcast[Int](_))
      val done = Source.maybe[Int].toMat(combined.watchTermination(Keep.right))(Keep.right).run()(mat)
      mat.shutdown()
      done.failed.futureValue shouldBe an[AbruptStageTerminationException]
    }

    "work with Sink.queue" in {
      val (queue, done) = Source(1 to 4).runWith(Sink.queue[Int]().watchTermination(Keep.both))
      queue.pull().futureValue should ===(Some(1))
      queue.pull().futureValue should ===(Some(2))
      queue.cancel()
      done.futureValue should ===(Done)
    }

    "signal termination once after single materialization value promise completed" in {
      val terminationSignal = Promise[Done]()

      class CompletingSink extends GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("CompletingSink.in")
        override val shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def onUpstreamFinish(): Unit = {
              terminationSignal.trySuccess(Done)
              completeStage()
            }
            setHandler(in, this)
          }
      }

      val done = Source(1 to 4).runWith(Sink.fromGraph(new CompletingSink).watchTermination(Keep.right))
      terminationSignal.future.futureValue should ===(Done)
      done.futureValue should ===(Done)
    }

    "fail future when stream is failed after the wrapped sink swapped its inlet handler" in {
      val ex = new RuntimeException("Stream failed.") with NoStackTrace
      val (p, done) = TestSource[Int]()
        .toMat(Sink.lazySink(() => Sink.ignore).watchTermination(Keep.right))(Keep.both)
        .run()
      p.sendNext(1)
      p.sendError(ex)
      whenReady(done.failed) { _ shouldBe ex }
    }

    "complete future when stream completes after the wrapped sink swapped its inlet handler" in {
      val (p, done) = TestSource[Int]()
        .toMat(Sink.lazySink(() => Sink.ignore).watchTermination(Keep.right))(Keep.both)
        .run()
      p.sendNext(1)
      p.sendNext(2)
      p.sendNext(3)
      p.sendComplete()
      done.futureValue should ===(Done)
    }

    "fail future when a handler of the wrapped sink throws" in {
      class FailingSink extends GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("FailingSink.in")
        override val shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = throw new RuntimeException("boom") with NoStackTrace
            setHandler(in, this)
          }
      }

      val done = Source.single(1).runWith(Sink.fromGraph(new FailingSink).watchTermination(Keep.right))
      done.failed.futureValue shouldBe a[RuntimeException]
    }

    "fail future when a fully fused stream abruptly terminated" in {
      val mat = Materializer(system)
      val done = Source.maybe[Int].toMat(Sink.ignore.watchTermination(Keep.right))(Keep.right).run()(mat)
      mat.shutdown()
      done.failed.futureValue shouldBe an[AbruptStageTerminationException]
    }

    "fail future when upstream of Sink.queue fails" in {
      val ex = new RuntimeException("Stream failed.") with NoStackTrace
      val (p, (queue, done)) =
        TestSource[Int]()
          .toMat(Sink.queue[Int]().watchTermination(Keep.both))(Keep.both)
          .run()
      p.sendNext(1)
      queue.pull().futureValue should ===(Some(1))
      p.sendError(ex)
      queue.pull().failed.futureValue shouldBe ex
      whenReady(done.failed) { _ shouldBe ex }
    }

    "work with a sink behind an async island" in {
      val done = Source(1 to 4).runWith(Sink.ignore.async.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "produce independent futures when the same blueprint is materialized multiple times" in {
      val watched = Sink.ignore.watchTermination(Keep.right)
      val done1 = Source(1 to 4).runWith(watched)
      val done2 = Source(5 to 8).runWith(watched)
      done1.futureValue should ===(Done)
      done2.futureValue should ===(Done)
      (done1 should not).be(done2)
    }

    "produce independent futures when the same blueprint is materialized concurrently" in {
      implicit val ec: ExecutionContext = system.dispatcher
      val watched = Sink.ignore.watchTermination(Keep.right)
      // Regression test: Sink blueprints must be safely re-materializable from concurrent threads.
      // A prior implementation shared a single mutable tracker field across all materializations of
      // the same blueprint, which raced when two threads materialized it at the same time and could
      // hang one of the resulting futures forever.
      for (_ <- 1 to 500) {
        val barrier = new CyclicBarrier(2)
        val f1 = Future {
          barrier.await()
          Source(1 to 3).runWith(watched)
        }.flatMap(identity)
        val f2 = Future {
          barrier.await()
          Source(4 to 6).runWith(watched)
        }.flatMap(identity)
        Await.result(f1, 5.seconds) should ===(Done)
        Await.result(f2, 5.seconds) should ===(Done)
      }
    }
  }
}
