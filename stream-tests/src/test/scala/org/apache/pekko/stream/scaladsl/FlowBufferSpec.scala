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

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.BufferOverflowException
import pekko.stream.OverflowStrategy
import pekko.stream.testkit._

@nowarn("msg=deprecated")
class FlowBufferSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 1
    pekko.stream.materializer.max-input-buffer-size = 1
  """) {

  "Buffer" must {

    "pass elements through normally in backpressured mode" in {
      val future: Future[Seq[Int]] =
        Source(1 to 1000).buffer(100, overflowStrategy = OverflowStrategy.backpressure).grouped(1001).runWith(Sink.head)
      Await.result(future, 3.seconds) should be(1 to 1000)
    }

    "pass elements through normally in backpressured mode with buffer size one" in {
      val future =
        Source(1 to 1000).buffer(1, overflowStrategy = OverflowStrategy.backpressure).grouped(1001).runWith(Sink.head)
      Await.result(future, 3.seconds) should be(1 to 1000)
    }

    "pass elements through a chain of backpressured buffers of different size" in {
      val future = Source(1 to 1000)
        .buffer(1, overflowStrategy = OverflowStrategy.backpressure)
        .buffer(10, overflowStrategy = OverflowStrategy.backpressure)
        .buffer(256, overflowStrategy = OverflowStrategy.backpressure)
        .buffer(1, overflowStrategy = OverflowStrategy.backpressure)
        .buffer(5, overflowStrategy = OverflowStrategy.backpressure)
        .buffer(128, overflowStrategy = OverflowStrategy.backpressure)
        .grouped(1001)
        .runWith(Sink.head)
      Await.result(future, 3.seconds) should be(1 to 1000)
    }

    "accept elements that fit in the buffer while downstream is silent" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .buffer(100, overflowStrategy = OverflowStrategy.backpressure)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      // Fill up buffer
      for (i <- 1 to 100) publisher.sendNext(i)

      // drain
      for (i <- 1 to 100) {
        sub.request(1)
        subscriber.expectNext(i)
      }
      sub.cancel()
    }

    "drop head elements if buffer is full and configured so" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .buffer(100, overflowStrategy = OverflowStrategy.dropHead)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      // Fill up buffer
      for (i <- 1 to 200) publisher.sendNext(i)

      // The next request would  be otherwise in race with the last onNext in the above loop
      subscriber.expectNoMessage(500.millis)

      // drain
      for (i <- 101 to 200) {
        sub.request(1)
        subscriber.expectNext(i)
      }

      sub.request(1)
      subscriber.expectNoMessage(1.seconds)

      publisher.sendNext(-1)
      sub.request(1)
      subscriber.expectNext(-1)

      sub.cancel()
    }

    "drop tail elements if buffer is full and configured so" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .buffer(100, overflowStrategy = OverflowStrategy.dropTail)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      // Fill up buffer
      for (i <- 1 to 200) publisher.sendNext(i)

      // The next request would  be otherwise in race with the last onNext in the above loop
      subscriber.expectNoMessage(500.millis)

      // drain
      for (i <- 1 to 99) {
        sub.request(1)
        subscriber.expectNext(i)
      }

      sub.request(1)
      subscriber.expectNext(200)

      sub.request(1)
      subscriber.expectNoMessage(1.seconds)

      publisher.sendNext(-1)
      sub.request(1)
      subscriber.expectNext(-1)

      sub.cancel()
    }

    "drop all elements if buffer is full and configured so" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .buffer(100, overflowStrategy = OverflowStrategy.dropBuffer)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      // Fill up buffer
      for (i <- 1 to 150) publisher.sendNext(i)

      // The next request would  be otherwise in race with the last onNext in the above loop
      subscriber.expectNoMessage(500.millis)

      // drain
      for (i <- 101 to 150) {
        sub.request(1)
        subscriber.expectNext(i)
      }

      sub.request(1)
      subscriber.expectNoMessage(1.seconds)

      publisher.sendNext(-1)
      sub.request(1)
      subscriber.expectNext(-1)

      sub.cancel()
    }

    "fail upstream if buffer is full and configured so" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .buffer(100, overflowStrategy = OverflowStrategy.fail)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      // Fill up buffer
      for (i <- 1 to 100) publisher.sendNext(i)

      // drain
      for (i <- 1 to 10) {
        sub.request(1)
        subscriber.expectNext(i)
      }

      // overflow the buffer
      for (i <- 101 to 111) publisher.sendNext(i)

      publisher.expectCancellation()
      val error = new BufferOverflowException("Buffer overflow (max capacity was: 100)!")
      subscriber.expectError(error)
    }

    for (strategy <- List(OverflowStrategy.dropHead, OverflowStrategy.dropTail, OverflowStrategy.dropBuffer)) {

      s"work with $strategy if buffer size of one" in {

        val publisher = TestPublisher.probe[Int]()
        val subscriber = TestSubscriber.manualProbe[Int]()

        Source.fromPublisher(publisher).buffer(1, overflowStrategy = strategy).to(Sink.fromSubscriber(subscriber)).run()
        val sub = subscriber.expectSubscription()

        // Fill up buffer
        for (i <- 1 to 200) publisher.sendNext(i)

        // The request below is in race otherwise with the onNext(200) above
        subscriber.expectNoMessage(500.millis)
        sub.request(1)
        subscriber.expectNext(200)

        sub.request(1)
        subscriber.expectNoMessage(1.seconds)

        publisher.sendNext(-1)
        sub.request(1)
        subscriber.expectNext(-1)

        sub.cancel()
      }
    }

  }
}
