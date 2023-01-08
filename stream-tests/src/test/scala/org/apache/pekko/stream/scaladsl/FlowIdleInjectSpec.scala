/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber

class FlowIdleInjectSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "keepAlive" must {

    "not emit additional elements if upstream is fast enough" in {
      Await.result(Source(1 to 10).keepAlive(1.second, () => 0).grouped(1000).runWith(Sink.head), 3.seconds) should ===(
        1 to 10)
    }

    "emit elements periodically after silent periods" in {
      val sourceWithIdleGap = Source(1 to 5) ++ Source(6 to 10).initialDelay(2.second)

      Await.result(sourceWithIdleGap.keepAlive(0.6.seconds, () => 0).grouped(1000).runWith(Sink.head),
        3.seconds) should ===(
        List(1, 2, 3, 4, 5, 0, 0, 0, 6, 7, 8, 9, 10))
    }

    "immediately pull upstream" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      Source.fromPublisher(upstream).keepAlive(1.second, () => 0).runWith(Sink.fromSubscriber(downstream))

      downstream.request(1)

      upstream.sendNext(1)
      downstream.expectNext(1)

      upstream.sendComplete()
      downstream.expectComplete()
    }

    "immediately pull upstream after busy period" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      (Source(1 to 10) ++ Source.fromPublisher(upstream))
        .keepAlive(1.second, () => 0)
        .runWith(Sink.fromSubscriber(downstream))

      downstream.request(10)
      downstream.expectNextN(1 to 10)

      downstream.request(1)

      upstream.sendNext(1)
      downstream.expectNext(1)

      upstream.sendComplete()
      downstream.expectComplete()
    }

    "work if timer fires before initial request" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      Source.fromPublisher(upstream).keepAlive(1.second, () => 0).runWith(Sink.fromSubscriber(downstream))

      downstream.ensureSubscription()
      downstream.expectNoMessage(1.5.second)
      downstream.request(1)
      downstream.expectNext(0)

      upstream.sendComplete()
      downstream.expectComplete()
    }

    "work if timer fires before initial request after busy period" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      (Source(1 to 10) ++ Source.fromPublisher(upstream))
        .keepAlive(1.second, () => 0)
        .runWith(Sink.fromSubscriber(downstream))

      downstream.request(10)
      downstream.expectNextN(1 to 10)

      downstream.expectNoMessage(1.5.second)
      downstream.request(1)
      downstream.expectNext(0)

      upstream.sendComplete()
      downstream.expectComplete()
    }

    "prefer upstream element over injected" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      Source.fromPublisher(upstream).keepAlive(1.second, () => 0).runWith(Sink.fromSubscriber(downstream))

      downstream.ensureSubscription()
      downstream.expectNoMessage(1.5.second)
      upstream.sendNext(1)
      downstream.expectNoMessage(0.5.second)
      downstream.request(1)
      downstream.expectNext(1)

      upstream.sendComplete()
      downstream.expectComplete()
    }

    "prefer upstream element over injected after busy period" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      (Source(1 to 10) ++ Source.fromPublisher(upstream))
        .keepAlive(1.second, () => 0)
        .runWith(Sink.fromSubscriber(downstream))

      downstream.request(10)
      downstream.expectNextN(1 to 10)

      downstream.expectNoMessage(1.5.second)
      upstream.sendNext(1)
      downstream.expectNoMessage(0.5.second)
      downstream.request(1)
      downstream.expectNext(1)

      upstream.sendComplete()
      downstream.expectComplete()
    }

    "reset deadline properly after injected element" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      Source.fromPublisher(upstream).keepAlive(1.second, () => 0).runWith(Sink.fromSubscriber(downstream))

      downstream.request(2)
      downstream.expectNoMessage(500.millis)
      downstream.expectNext(0)

      downstream.expectNoMessage(500.millis)
      downstream.expectNext(0)
    }

  }

}
