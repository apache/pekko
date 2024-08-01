/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.util.Success
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.Done
import pekko.stream.AbruptStageTerminationException
import pekko.stream.ActorAttributes
import pekko.stream.ActorMaterializer
import pekko.stream.Supervision
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.Utils.TE
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource
import pekko.testkit.EventFilter

class FlowStatefulMapSpec extends StreamSpec {

  val ex = new Exception("TEST") with NoStackTrace

  object BeenCalledTimesGate {
    def apply(): BeenCalledTimesGate = new BeenCalledTimesGate(1)

    def apply(nTimes: Int): BeenCalledTimesGate = new BeenCalledTimesGate(nTimes)
  }

  class BeenCalledTimesGate(nTimes: Int) {
    private val beenCalled = new AtomicInteger(0)

    def mark(): Unit = beenCalled.updateAndGet { current =>
      if (current == nTimes) {
        throw new IllegalStateException(s"Has been called:[$nTimes] times, should not be called anymore.")
      } else current + 1
    }

    def ensure(): Unit = if (beenCalled.get() != nTimes) {
      throw new IllegalStateException(s"Expected to be called:[$nTimes], but only be called:[$beenCalled]")
    }
  }

  "A StatefulMap" must {
    "work in the happy case" in {
      val gate = BeenCalledTimesGate()
      val sinkProb = Source(List(1, 2, 3, 4, 5))
        .statefulMap(() => 0)((agg, elem) =>
            (agg + elem, (agg, elem)),
          _ => {
            gate.mark()
            None
          })
        .runWith(TestSink.probe[(Int, Int)])
      sinkProb.expectSubscription().request(6)
      sinkProb
        .expectNext((0, 1))
        .expectNext((1, 2))
        .expectNext((3, 3))
        .expectNext((6, 4))
        .expectNext((10, 5))
        .expectComplete()
      gate.ensure()
    }

    "can remember the state when complete" in {
      val gate = BeenCalledTimesGate()
      val sinkProb = Source(1 to 10)
        .statefulMap(() => List.empty[Int])(
          (state, elem) => {
            // grouped 3 elements into a list
            val newState = elem :: state
            if (newState.size == 3)
              (Nil, newState.reverse)
            else
              (newState, Nil)
          },
          state => {
            gate.mark()
            Some(state.reverse)
          })
        .mapConcat(identity)
        .runWith(TestSink.probe[Int])
      sinkProb.expectSubscription().request(10)
      sinkProb.expectNextN(List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)).expectComplete()
      gate.ensure()
    }

    "be able to resume" in {
      val gate = BeenCalledTimesGate()
      val testSink = Source(List(1, 2, 3, 4, 5))
        .statefulMap(() => 0)((agg, elem) =>
            if (elem % 2 == 0)
              throw ex
            else
              (agg + elem, (agg, elem)),
          _ => {
            gate.mark()
            None
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(TestSink.probe[(Int, Int)])

      testSink.expectSubscription().request(5)
      testSink.expectNext((0, 1)).expectNext((1, 3)).expectNext((4, 5)).expectComplete()
      gate.ensure()
    }

    "be able to restart" in {
      val gate = BeenCalledTimesGate(2)
      val testSink = Source(List(1, 2, 3, 4, 5))
        .statefulMap(() => 0)((agg, elem) =>
            if (elem % 3 == 0)
              throw ex
            else
              (agg + elem, (agg, elem)),
          _ => {
            gate.mark()
            None
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .runWith(TestSink.probe[(Int, Int)])

      testSink.expectSubscription().request(5)
      testSink.expectNext((0, 1)).expectNext((1, 2)).expectNext((0, 4)).expectNext((4, 5)).expectComplete()
      gate.ensure()
    }

    "be able to stop" in {
      val gate = BeenCalledTimesGate()
      val testSink = Source(List(1, 2, 3, 4, 5))
        .statefulMap(() => 0)((agg, elem) =>
            if (elem % 3 == 0)
              throw ex
            else
              (agg + elem, (agg, elem)),
          _ => {
            gate.mark()
            None
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .runWith(TestSink.probe[(Int, Int)])

      testSink.expectSubscription().request(5)
      testSink.expectNext((0, 1)).expectNext((1, 2)).expectError(ex)
      gate.ensure()
    }

    "fail on upstream failure" in {
      val gate = BeenCalledTimesGate()
      val (testSource, testSink) = TestSource
        .probe[Int]
        .statefulMap(() => 0)((agg, elem) =>
            (agg + elem, (agg, elem)),
          _ => {
            gate.mark()
            None
          })
        .toMat(TestSink.probe[(Int, Int)])(Keep.both)
        .run()

      testSink.expectSubscription().request(5)
      testSource.sendNext(1)
      testSink.expectNext((0, 1))
      testSource.sendNext(2)
      testSink.expectNext((1, 2))
      testSource.sendNext(3)
      testSink.expectNext((3, 3))
      testSource.sendNext(4)
      testSink.expectNext((6, 4))
      testSource.sendError(ex)
      testSink.expectError(ex)
      gate.ensure()
    }

    "defer upstream failure and remember state" in {
      val gate = BeenCalledTimesGate()
      val (testSource, testSink) = TestSource
        .probe[Int]
        .statefulMap(() => 0)((agg, elem) =>
            (agg + elem, (agg, elem)),
          (state: Int) => {
            gate.mark()
            Some((state, -1))
          })
        .toMat(TestSink.probe[(Int, Int)])(Keep.both)
        .run()

      testSink.expectSubscription().request(5)
      testSource.sendNext(1)
      testSink.expectNext((0, 1))
      testSource.sendNext(2)
      testSink.expectNext((1, 2))
      testSource.sendNext(3)
      testSink.expectNext((3, 3))
      testSource.sendNext(4)
      testSink.expectNext((6, 4))
      testSource.sendError(ex)
      testSink.expectNext((10, -1))
      testSink.expectError(ex)
      gate.ensure()
    }

    "cancel upstream when downstream cancel" in {
      val gate = BeenCalledTimesGate()
      val promise = Promise[Done]()
      val testSource = TestSource
        .probe[Int]
        .statefulMap(() => 100)((agg, elem) =>
            (agg + elem, (agg, elem)),
          (state: Int) => {
            gate.mark()
            promise.complete(Success(Done))
            Some((state, -1))
          })
        .toMat(Sink.cancelled)(Keep.left)
        .run()
      testSource.expectSubscription().expectCancellation()
      Await.result(promise.future, 3.seconds) shouldBe Done
      gate.ensure()
    }

    "cancel upstream when downstream fail" in {
      val gate = BeenCalledTimesGate()
      val promise = Promise[Done]()
      val testProb = TestSubscriber.probe[(Int, Int)]()
      val testSource = TestSource
        .probe[Int]
        .statefulMap(() => 100)((agg, elem) =>
            (agg + elem, (agg, elem)),
          (state: Int) => {
            gate.mark()
            promise.complete(Success(Done))
            Some((state, -1))
          })
        .toMat(Sink.fromSubscriber(testProb))(Keep.left)
        .run()
      testProb.cancel(ex)
      testSource.expectCancellationWithCause(ex)
      Await.result(promise.future, 3.seconds) shouldBe Done
      gate.ensure()
    }

    "call its onComplete callback on abrupt materializer termination" in {
      val gate = BeenCalledTimesGate()
      @nowarn("msg=deprecated")
      val mat = ActorMaterializer()
      val promise = Promise[Done]()

      val matVal = Source
        .single(1)
        .statefulMap(() => -1)((_, elem) => (elem, elem),
          _ => {
            gate.mark()
            promise.complete(Success(Done))
            None
          })
        .runWith(Sink.never)(mat)
      mat.shutdown()
      matVal.failed.futureValue shouldBe a[AbruptStageTerminationException]
      Await.result(promise.future, 3.seconds) shouldBe Done
      gate.ensure()
    }

    "call its onComplete callback when stop" in {
      val gate = BeenCalledTimesGate()
      val promise = Promise[Done]()
      Source
        .single(1)
        .statefulMap(() => -1)((_, elem) => {
            throw ex
            (elem, elem)
          },
          _ => {
            gate.mark()
            promise.complete(Success(Done))
            None
          })
        .runWith(Sink.ignore)
      Await.result(promise.future, 3.seconds) shouldBe Done
      gate.ensure()
    }

    "be able to be used as zipWithIndex" in {
      val gate = BeenCalledTimesGate()
      Source(List("A", "B", "C", "D"))
        .statefulMap(() => 0L)((index, elem) => (index + 1, (elem, index)),
          _ => {
            gate.mark()
            None
          })
        .runWith(TestSink.probe[(String, Long)])
        .request(4)
        .expectNext(("A", 0L))
        .expectNext(("B", 1L))
        .expectNext(("C", 2L))
        .expectNext(("D", 3L))
        .expectComplete()
      gate.ensure()
    }

    "be able to be used as bufferUntilChanged" in {
      val gate = BeenCalledTimesGate()
      val sink = TestSink.probe[List[String]]
      Source("A" :: "B" :: "B" :: "C" :: "C" :: "C" :: "D" :: Nil)
        .statefulMap(() => List.empty[String])(
          (buffer, elem) =>
            buffer match {
              case head :: _ if head != elem => (elem :: Nil, buffer)
              case _                         => (elem :: buffer, Nil)
            },
          buffer => {
            gate.mark()
            Some(buffer)
          })
        .filter(_.nonEmpty)
        .alsoTo(Sink.foreach(println))
        .runWith(sink)
        .request(4)
        .expectNext(List("A"))
        .expectNext(List("B", "B"))
        .expectNext(List("C", "C", "C"))
        .expectNext(List("D"))
        .expectComplete()
      gate.ensure()
    }

    "be able to be used as distinctUntilChanged" in {
      val gate = BeenCalledTimesGate()
      Source("A" :: "B" :: "B" :: "C" :: "C" :: "C" :: "D" :: Nil)
        .statefulMap(() => Option.empty[String])(
          (lastElement, elem) =>
            lastElement match {
              case Some(head) if head == elem => (Some(elem), None)
              case _                          => (Some(elem), Some(elem))
            },
          _ => {
            gate.mark()
            None
          })
        .collect { case Some(elem) => elem }
        .runWith(TestSink.probe[String])
        .request(4)
        .expectNext("A")
        .expectNext("B")
        .expectNext("C")
        .expectNext("D")
        .expectComplete()
      gate.ensure()
    }

    "will not call `onComplete` twice if `f` fail" in {
      val closedCounter = new AtomicInteger(0)
      val probe = Source
        .repeat(1)
        .statefulMap(() => "opening resource")(
          (_, _) => throw TE("failing read"),
          _ => {
            closedCounter.incrementAndGet()
            None
          })
        .runWith(TestSink.probe[String])

      probe.request(1)
      probe.expectError(TE("failing read"))
      closedCounter.get() should ===(1)
    }

    "will not call `onComplete` twice if both `f` and `onComplete` fail" in {
      val closedCounter = new AtomicInteger(0)
      val probe = Source
        .repeat(1)
        .statefulMap(() => "opening resource")((_, _) => throw TE("failing read"),
          _ => {
            if (closedCounter.incrementAndGet() == 1) {
              throw TE("boom")
            }
            None
          })
        .runWith(TestSink.probe[Int])

      EventFilter[TE](occurrences = 1).intercept {
        probe.request(1)
        probe.expectError(TE("boom"))
      }
      closedCounter.get() should ===(1)
    }

    "will not call `onComplete` twice if `onComplete` fail on upstream complete" in {
      val closedCounter = new AtomicInteger(0)
      val (pub, sub) = TestSource[Int]()
        .statefulMap(() => "opening resource")((state, value) => (state, value),
          _ => {
            closedCounter.incrementAndGet()
            throw TE("boom")
          })
        .toMat(TestSink.probe[Int])(Keep.both)
        .run()

      EventFilter[TE](occurrences = 1).intercept {
        sub.request(1)
        pub.sendNext(1)
        sub.expectNext(1)
        sub.request(1)
        pub.sendComplete()
        sub.expectError(TE("boom"))
      }
      closedCounter.get() shouldBe 1
    }
  }
}
