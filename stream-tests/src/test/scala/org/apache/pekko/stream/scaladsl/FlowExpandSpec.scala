/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko

import pekko.stream.ActorAttributes
import pekko.stream.Supervision
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource

class FlowExpandSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
    pekko.stream.materializer.max-input-buffer-size = 2
  """) {

  "Expand" must {

    "pass-through elements unchanged when there is no rate difference" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()

      // Simply repeat the last element as an extrapolation step
      Source
        .fromPublisher(publisher)
        .expand(Iterator.single)
        .to(Sink.fromSubscriber(subscriber))
        // Shadow the fuzzed materializer (see the ordering guarantee needed by the for loop below).
        .withAttributes(ActorAttributes.fuzzingMode(false))
        .run()

      for (i <- 1 to 100) {
        // Order is important here: If the request comes first it will be extrapolated!
        publisher.sendNext(i)
        subscriber.requestNext(i)
      }

      subscriber.cancel()
    }

    "expand elements while upstream is silent" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()

      // Simply repeat the last element as an extrapolation step
      Source.fromPublisher(publisher).expand(Iterator.continually(_)).to(Sink.fromSubscriber(subscriber)).run()

      publisher.sendNext(42)

      for (_ <- 1 to 100) {
        subscriber.requestNext(42)
      }

      publisher.sendNext(-42)

      // The request below is otherwise in race with the above sendNext
      subscriber.expectNoMessage(500.millis)
      subscriber.requestNext(-42)

      subscriber.cancel()
    }

    "do not drop last element" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()

      // Simply repeat the last element as an extrapolation step
      Source.fromPublisher(publisher).expand(Iterator.continually(_)).to(Sink.fromSubscriber(subscriber)).run()

      publisher.sendNext(1)
      subscriber.requestNext(1)

      publisher.sendNext(2)
      publisher.sendComplete()

      // The request below is otherwise in race with the above sendNext(2) (and completion)
      subscriber.expectNoMessage(500.millis)

      subscriber.requestNext(2)
      subscriber.expectComplete()
    }

    "work on a variable rate chain" in {
      val future = Source(1 to 100)
        .map { i =>
          if (ThreadLocalRandom.current().nextBoolean()) Thread.sleep(10); i
        }
        .expand(Iterator.continually(_))
        .runFold(Set.empty[Int])(_ + _)

      Await.result(future, 10.seconds) should contain theSameElementsAs (1 to 100).toSet
    }

    "backpressure publisher when subscriber is slower" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()

      Source.fromPublisher(publisher).expand(Iterator.continually(_)).to(Sink.fromSubscriber(subscriber)).run()

      publisher.sendNext(1)
      subscriber.requestNext(1)
      subscriber.requestNext(1)

      var pending = publisher.pending
      // Deplete pending requests coming from input buffer
      while (pending > 0) {
        publisher.unsafeSendNext(2)
        pending -= 1
      }

      // The above sends are absorbed in the input buffer, and will result in two one-sized batch requests
      pending += publisher.expectRequest()
      pending += publisher.expectRequest()
      while (pending > 0) {
        publisher.unsafeSendNext(2)
        pending -= 1
      }

      publisher.expectNoMessage(1.second)

      subscriber.request(2)
      subscriber.expectNext(2)
      subscriber.expectNext(2)

      // Now production is resumed
      publisher.expectRequest()

    }

    "work properly with finite extrapolations" in {
      val (source, sink) =
        TestSource[Int]().expand(i => Iterator.from(0).map(i -> _).take(3)).toMat(TestSink())(Keep.both).run()
      source.sendNext(1)
      sink.request(4).expectNext(1 -> 0, 1 -> 1, 1 -> 2).expectNoMessage(100.millis)
      source.sendNext(2).sendComplete()
      sink.expectNext(2 -> 0).expectComplete()
    }

    "fail stream when expander throws and supervision is Stop" in {
      val ex = new RuntimeException("boom")
      val result = Source(1 to 5)
        .expand(i => if (i == 3) throw ex else Iterator.single(i))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .runWith(Sink.ignore)
      result.failed.futureValue shouldBe ex
    }

    "fail stream when expander throws and supervision defaults to Stop" in {
      val ex = new RuntimeException("boom")
      val result = Source(1 to 5)
        .expand(i => if (i == 3) throw ex else Iterator.single(i))
        .runWith(Sink.ignore)
      result.failed.futureValue shouldBe ex
    }

    "resume and keep current extrapolation when expander throws" in {
      val ex = new RuntimeException("boom")
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()

      Source
        .fromPublisher(publisher)
        .expand(i => if (i == 1) Iterator(1, 10, 11) else if (i == 2) throw ex else Iterator.single(i))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()

      publisher.sendNext(1)
      subscriber.requestNext(1)
      publisher.sendNext(2) // throws in expander, element 2 is dropped under Resume
      subscriber.requestNext(10) // continue from current extrapolation
      subscriber.cancel()
    }

    "complete immediately on upstream finish if expanded is true" in {
      val subscriber = TestSubscriber.probe[Int]()

      Source.single(1)
        .expand(_ => Iterator(1, 2, 3))
        .to(Sink.fromSubscriber(subscriber))
        .run()

      subscriber.requestNext(1) // expanded becomes true
      subscriber.expectComplete() // must complete immediately when expanded is true
    }

    "restart and reset current extrapolation when expander throws" in {
      val ex = new RuntimeException("boom")
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()

      Source
        .fromPublisher(publisher)
        .expand(i => if (i == 1) Iterator(1, 10, 11) else if (i == 2) throw ex else Iterator.single(i))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()

      publisher.sendNext(1)
      subscriber.requestNext(1)
      publisher.sendNext(2) // throws in expander, restart resets iterator state
      subscriber.request(1)
      subscriber.expectNoMessage(300.millis)
      publisher.sendNext(3)
      subscriber.requestNext(3)
      subscriber.cancel()
    }

    "resume when iterator produced by expander throws during iteration" in {
      val ex = new RuntimeException("boom")
      val result = Source(1 to 4)
        .expand(i =>
          if (i == 3)
            new Iterator[Int] {
              override def hasNext: Boolean = true
              override def next(): Int = throw ex
            }
          else Iterator.single(i))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(Sink.seq)

      Await.result(result, 3.seconds) shouldBe Seq(1, 2, 4)
    }

    "restart when iterator produced by expander throws during iteration" in {
      val ex = new RuntimeException("boom")
      val result = Source(1 to 4)
        .expand(i =>
          if (i == 3)
            new Iterator[Int] {
              override def hasNext: Boolean = true
              override def next(): Int = throw ex
            }
          else Iterator.single(i))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .runWith(Sink.seq)

      Await.result(result, 3.seconds) shouldBe Seq(1, 2, 4)
    }

    "fail stream when iterator throws during upstream completion" in {
      val ex = new RuntimeException("boom")
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()

      Source
        .fromPublisher(publisher)
        .expand(_ =>
          new Iterator[Int] {
            private var calls = 0
            override def hasNext: Boolean = {
              calls += 1
              if (calls >= 2) throw ex else true
            }
            override def next(): Int = 1
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()

      subscriber.request(1) // ensure isAvailable(out) during onPush so hasNext #1 fires there
      publisher.sendNext(1) // onPush: hasNext #1 true, pull
      subscriber.expectNext(1)
      publisher.sendComplete() // onUpstreamFinish: hasNext #2 throws -> failStage
      subscriber.expectError(ex)
    }

    "resume and complete when iterator throws during upstream completion with no pending pull" in {
      val ex = new RuntimeException("boom")
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()

      Source
        .fromPublisher(publisher)
        .expand(_ =>
          new Iterator[Int] {
            private var calls = 0
            override def hasNext: Boolean = {
              calls += 1
              if (calls >= 2) throw ex else true
            }
            override def next(): Int = 1
          })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()

      subscriber.request(1) // ensure isAvailable(out) during onPush so hasNext #1 fires there
      publisher.sendNext(1) // onPush: hasNext #1 true, pull
      subscriber.expectNext(1)
      publisher.sendComplete() // onUpstreamFinish: hasNext #2 throws -> resume -> completeStage
      subscriber.expectComplete()
    }

    "resume when iterator throws during onPull with expanded = true" in {
      val ex = new RuntimeException("boom")
      val (publisher, subscriber) = TestSource[Int]()
        .expand(i =>
          if (i == 2)
            new Iterator[Int] {
              private var calls = 0
              override def hasNext: Boolean = {
                calls += 1
                // 1st hasNext (in onPush) succeeds -> element 2 pushed, expanded=true
                // 2nd hasNext (in onPull during extrapolation) throws
                if (calls >= 2) throw ex else true
              }
              override def next(): Int = 2
            }
          else Iterator.single(i))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      subscriber.request(1)
      publisher.sendNext(1)
      subscriber.expectNext(1)

      // hasNext #1 succeeds in onPush, element 2 emitted, expanded=true
      subscriber.request(1)
      publisher.sendNext(2)
      subscriber.expectNext(2)

      // hasNext #2 throws in onPull; Resume resets state, pulls next
      subscriber.request(1)

      // Element 3 flows through normally
      publisher.sendNext(3)
      subscriber.expectNext(3)

      publisher.sendComplete()
      subscriber.expectComplete()
    }
  }

}
