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

import org.apache.pekko.stream.testkit._

class SubscriberSinkSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "A Flow with SubscriberSink" must {

    "publish elements to the subscriber" in {
      val c = TestSubscriber.manualProbe[Int]()
      Source(List(1, 2, 3)).to(Sink.fromSubscriber(c)).run()
      val s = c.expectSubscription()
      s.request(3)
      c.expectNext(1)
      c.expectNext(2)
      c.expectNext(3)
      c.expectComplete()
    }
  }

}
