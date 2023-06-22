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

import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.TestSubscriber
import pekko.testkit.DefaultTimeout

class FailedSourceSpec extends StreamSpec with DefaultTimeout {

  "The Failed Source" must {
    "emit error immediately" in {
      val ex = new RuntimeException with NoStackTrace
      val p = Source.failed(ex).runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[Int]()
      p.subscribe(c)
      c.expectSubscriptionAndError(ex)

      // reject additional subscriber
      val c2 = TestSubscriber.manualProbe[Int]()
      p.subscribe(c2)
      c2.expectSubscriptionAndError()
    }
  }

}
