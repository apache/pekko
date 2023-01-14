/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.testkit.{ StreamSpec, TestSubscriber }
import pekko.testkit.DefaultTimeout

class NeverSourceSpec extends StreamSpec with DefaultTimeout {

  "The Never Source" must {

    "never completes" in {
      val neverSource = Source.never[Int]
      val pubSink = Sink.asPublisher[Int](false)

      val neverPub = neverSource.toMat(pubSink)(Keep.right).run()

      val c = TestSubscriber.manualProbe[Int]()
      neverPub.subscribe(c)
      val subs = c.expectSubscription()

      subs.request(1)
      c.expectNoMessage(300.millis)

      subs.cancel()
    }
  }
}
