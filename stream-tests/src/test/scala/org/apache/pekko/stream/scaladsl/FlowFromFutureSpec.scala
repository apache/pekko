/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.annotation.nowarn
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import org.apache.pekko.stream.testkit._
import org.apache.pekko.stream.testkit.scaladsl.TestSink

@nowarn("msg=deprecated") // testing deprecated API
class FlowFromFutureSpec extends StreamSpec {

  "A Flow based on a Future" must {
    "produce one element from already successful Future" in {
      val c = TestSubscriber.manualProbe[Int]()
      Source.future(Future.successful(1)).runWith(Sink.asPublisher(true)).subscribe(c)
      val sub = c.expectSubscription()
      c.expectNoMessage(100.millis)
      sub.request(1)
      c.expectNext(1)
      c.expectComplete()
    }

    "produce error from already failed Future" in {
      val ex = new RuntimeException("test") with NoStackTrace
      val c = TestSubscriber.manualProbe[Int]()
      Source.future(Future.failed[Int](ex)).runWith(Sink.asPublisher(false)).subscribe(c)
      c.expectSubscriptionAndError(ex)
    }

    "fails flow from already failed Future even no demands" in {
      val ex = new RuntimeException("test") with NoStackTrace
      val sub = Source.future(Future.failed[Int](ex))
        .runWith(TestSink.probe)
      sub.expectSubscriptionAndError(ex)
    }

    "produce one element when Future is completed" in {
      val promise = Promise[Int]()
      val c = TestSubscriber.manualProbe[Int]()
      Source.future(promise.future).runWith(Sink.asPublisher(true)).subscribe(c)
      val sub = c.expectSubscription()
      sub.request(1)
      c.expectNoMessage(100.millis)
      promise.success(1)
      c.expectNext(1)
      c.expectComplete()
      c.expectNoMessage(100.millis)
    }

    "produce one element when Future is completed but not before request" in {
      val promise = Promise[Int]()
      val c = TestSubscriber.manualProbe[Int]()
      Source.future(promise.future).runWith(Sink.asPublisher(true)).subscribe(c)
      val sub = c.expectSubscription()
      promise.success(1)
      c.expectNoMessage(200.millis)
      sub.request(1)
      c.expectNext(1)
      c.expectComplete()
    }

    "produce elements with multiple subscribers" in {
      val promise = Promise[Int]()
      val p = Source.future(promise.future).runWith(Sink.asPublisher(true))
      val c1 = TestSubscriber.manualProbe[Int]()
      val c2 = TestSubscriber.manualProbe[Int]()
      p.subscribe(c1)
      p.subscribe(c2)
      val sub1 = c1.expectSubscription()
      val sub2 = c2.expectSubscription()
      sub1.request(1)
      promise.success(1)
      sub2.request(2)
      c1.expectNext(1)
      c2.expectNext(1)
      c1.expectComplete()
      c2.expectComplete()
    }

    "allow cancel before receiving element" in {
      val promise = Promise[Int]()
      val p = Source.future(promise.future).runWith(Sink.asPublisher(true))
      val keepAlive = TestSubscriber.manualProbe[Int]()
      val c = TestSubscriber.manualProbe[Int]()
      p.subscribe(keepAlive)
      p.subscribe(c)
      val sub = c.expectSubscription()
      sub.request(1)
      sub.cancel()
      c.expectNoMessage(500.millis)
      promise.success(1)
      c.expectNoMessage(200.millis)
    }
  }
}
