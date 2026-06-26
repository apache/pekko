/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.ActorAttributes
import pekko.stream.OverflowStrategy
import pekko.stream.Supervision
import pekko.stream.testkit._

class FlowBatchSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
    pekko.stream.materializer.max-input-buffer-size = 2
  """) {

  "Batch" must {

    "pass-through elements unchanged when there is no rate difference" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .batch(max = 2, seed = i => i)(aggregate = _ + _)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      for (i <- 1 to 100) {
        sub.request(1)
        publisher.sendNext(i)
        subscriber.expectNext(i)
      }

      sub.cancel()
    }

    "aggregate elements while downstream is silent" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[List[Int]]()

      Source
        .fromPublisher(publisher)
        .batch(max = Long.MaxValue, seed = i => List(i))(aggregate = (ints, i) => i :: ints)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      for (i <- 1 to 10) {
        publisher.sendNext(i)
      }
      subscriber.expectNoMessage(1.second)
      sub.request(1)
      subscriber.expectNext(List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1))
      sub.cancel()
    }

    "work on a variable rate chain" in {
      val future = Source(1 to 1000)
        .batch(max = 100, seed = i => i)(aggregate = (sum, i) => sum + i)
        .map { i =>
          if (ThreadLocalRandom.current().nextBoolean()) Thread.sleep(10); i
        }
        .runFold(0)(_ + _)
      Await.result(future, 10.seconds) should be(500500)
    }

    "backpressure subscriber when upstream is slower" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .batch(max = 2, seed = i => i)(aggregate = _ + _)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      sub.request(1)
      publisher.sendNext(1)
      subscriber.expectNext(1)

      sub.request(1)
      subscriber.expectNoMessage(500.millis)
      publisher.sendNext(2)
      subscriber.expectNext(2)

      publisher.sendNext(3)
      publisher.sendNext(4)
      // The request can be in race with the above onNext(4) so the result would be either 3 or 7.
      subscriber.expectNoMessage(500.millis)
      sub.request(1)
      subscriber.expectNext(7)

      sub.request(1)
      subscriber.expectNoMessage(500.millis)
      sub.cancel()

    }

    "work with a buffer and fold" in {
      val future = Source(1 to 50)
        .batch(max = Long.MaxValue, seed = i => i)(aggregate = _ + _)
        .buffer(50, OverflowStrategy.backpressure)
        .runFold(0)(_ + _)
      Await.result(future, 3.seconds) should be((1 to 50).sum)
    }

    "fail stream when costFn throws and supervision is Stop" in {
      val ex = new RuntimeException("boom")
      val result = Source(1 to 5)
        .batchWeighted(
          max = 10,
          costFn = (i: Int) => if (i == 3) throw ex else i.toLong,
          seed = (i: Int) => i)(aggregate = _ + _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .runWith(Sink.ignore)
      result.failed.futureValue shouldBe ex
    }

    "fail stream when costFn throws and supervision defaults to Stop" in {
      // No supervision attribute is set, so the mandatory SupervisionStrategy attribute
      // resolves to the default stopping decider. This guards the default code path.
      val ex = new RuntimeException("boom")
      val result = Source(1 to 5)
        .batchWeighted(
          max = 10,
          costFn = (i: Int) => if (i == 3) throw ex else i.toLong,
          seed = (i: Int) => i)(aggregate = _ + _)
        .runWith(Sink.ignore)
      result.failed.futureValue shouldBe ex
    }

    "resume when costFn throws and supervision is Resume" in {
      val future = Source(1 to 5)
        .batchWeighted(
          max = Long.MaxValue,
          costFn = (i: Int) => if (i == 3) throw new RuntimeException("boom") else i.toLong,
          seed = (i: Int) => i)(aggregate = _ + _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runFold(0)(_ + _)
      Await.result(future, 3.seconds) should be(12) // 1 + 2 + 4 + 5, 3 is skipped
    }

    // The next two tests are a directional pair: with downstream silent the whole stream
    // aggregates into a single batch, so the emitted value reveals whether the partial
    // batch accumulated before the failing element was kept (Resume) or discarded (Restart).
    "keep the accumulated batch when costFn throws and supervision is Resume" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .batchWeighted(
          max = Long.MaxValue,
          costFn = (i: Int) => if (i == 3) throw new RuntimeException("boom") else 1L,
          seed = (i: Int) => i)(aggregate = _ + _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      for (i <- 1 to 5) publisher.sendNext(i)
      publisher.sendComplete()
      subscriber.expectNoMessage(300.millis)
      sub.request(1)
      // 1 + 2 accumulated, element 3 skipped but the partial batch is preserved, then 4 + 5 added
      subscriber.expectNext(1 + 2 + 4 + 5)
      subscriber.expectComplete()
    }

    "reset the accumulated batch when costFn throws and supervision is Restart" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .batchWeighted(
          max = Long.MaxValue,
          costFn = (i: Int) => if (i == 3) throw new RuntimeException("boom") else 1L,
          seed = (i: Int) => i)(aggregate = _ + _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      for (i <- 1 to 5) publisher.sendNext(i)
      publisher.sendComplete()
      subscriber.expectNoMessage(300.millis)
      sub.request(1)
      // 1 + 2 accumulated, then discarded on Restart, element 3 skipped, only 4 + 5 remain
      subscriber.expectNext(4 + 5)
      subscriber.expectComplete()
    }

    "restart and continue when pending element costFn throws during flush" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()
      var callsForThree = 0

      Source
        .fromPublisher(publisher)
        .batchWeighted(
          max = 3,
          costFn = (i: Int) => {
            if (i == 3) {
              callsForThree += 1
              if (callsForThree == 2) throw new RuntimeException("boom")
            }
            i.toLong
          },
          seed = (i: Int) => i)(aggregate = _ + _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      // 3 is evaluated once in onPush (becomes pending because left < cost) and again in flush.
      publisher.sendNext(1)
      publisher.sendNext(2)
      publisher.sendNext(3)
      subscriber.expectNoMessage(300.millis)

      sub.request(1)
      subscriber.expectNext(3)

      // After restart in flush(), the stage must still pull and continue processing new elements.
      publisher.sendNext(4)
      publisher.sendComplete()
      sub.request(1)
      subscriber.expectNext(4)
      subscriber.expectComplete()

      callsForThree shouldBe 2
    }

    "resume and continue when pending element costFn throws during flush" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()
      var callsForThree = 0

      Source
        .fromPublisher(publisher)
        .batchWeighted(
          max = 2,
          costFn = (i: Int) => {
            if (i == 3) {
              callsForThree += 1
              if (callsForThree == 2) throw new RuntimeException("boom")
            }
            1L
          },
          seed = (i: Int) => i)(aggregate = _ + _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      // 3 is evaluated once in onPush (becomes pending) and again in flush.
      publisher.sendNext(1)
      publisher.sendNext(2)
      publisher.sendNext(3)
      subscriber.expectNoMessage(300.millis)

      sub.request(1)
      subscriber.expectNext(3)

      // On Resume, pending should be dropped without leaking the previously emitted batch into new state.
      publisher.sendNext(4)
      publisher.sendComplete()
      sub.request(1)
      subscriber.expectNext(4)
      subscriber.expectComplete()

      callsForThree shouldBe 2
    }

    "stop with the original exception when pending element costFn throws during flush" in {
      val ex = new RuntimeException("boom")
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()
      var callsForThree = 0

      Source
        .fromPublisher(publisher)
        .batchWeighted(
          max = 2,
          costFn = (i: Int) => {
            if (i == 3) {
              callsForThree += 1
              if (callsForThree == 2) throw ex
            }
            1L
          },
          seed = (i: Int) => i)(aggregate = _ + _)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      publisher.sendNext(1)
      publisher.sendNext(2)
      publisher.sendNext(3)
      subscriber.expectNoMessage(300.millis)

      sub.request(1)
      subscriber.expectNext(3)
      subscriber.expectError(ex)

      callsForThree shouldBe 2
    }

    "complete when pending seed throws at end of stream and supervision is Restart" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .batchWeighted(
          max = 1,
          costFn = (_: Int) => 1L,
          seed = (i: Int) => if (i == 2) throw new RuntimeException("boom") else i)(aggregate = _ + _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      publisher.sendNext(1)
      publisher.sendNext(2)
      publisher.sendComplete()
      sub.request(1)
      subscriber.expectNext(1)
      subscriber.expectComplete()
    }

    "resume when seed throws in onPush and supervision is Resume" in {
      // seed is only called in onPush when agg == null (i.e. no active batch).
      // Under Resume the failing element must be dropped and the next element
      // should start a fresh batch.
      val future = Source(1 to 5)
        .batchWeighted(
          max = Long.MaxValue,
          costFn = (i: Int) => i.toLong,
          seed = (i: Int) => if (i == 1) throw new RuntimeException("boom") else i)(aggregate = _ + _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runFold(0)(_ + _)
      Await.result(future, 3.seconds) should be(2 + 3 + 4 + 5)
    }

    "resume when aggregate throws in onPush and supervision is Resume" in {
      // aggregate is only called in onPush when downstream backpressures and a batch
      // is accumulating. Use a manual subscriber to force batching. Under Resume
      // the failing element must be dropped while the accumulated batch is preserved.
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .batchWeighted(
          max = 10,
          costFn = (_: Int) => 1L,
          seed = (i: Int) => i)(aggregate =
          (acc, i) => if (i == 3) throw new RuntimeException("boom") else acc + i)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      // Send elements without requesting; they accumulate in a single batch.
      publisher.sendNext(1) // seed -> agg=1
      publisher.sendNext(2) // aggregate(1,2) -> agg=3
      publisher.sendNext(3) // aggregate(3,3) throws -> Resume, agg stays 3, element 3 dropped
      publisher.sendNext(4) // aggregate(3,4) -> agg=7
      publisher.sendNext(5) // aggregate(7,5) -> agg=12
      publisher.sendComplete()

      subscriber.expectNoMessage(300.millis)
      sub.request(1)
      subscriber.expectNext(1 + 2 + 4 + 5)
      subscriber.expectComplete()
    }

  }
}
