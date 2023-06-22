/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink

class FlowDetacherSpec extends StreamSpec {

  "A Detacher" must {

    "pass through all elements" in {
      Source(1 to 100).detach.runWith(Sink.seq).futureValue should ===(1 to 100)
    }

    "pass through failure" in {
      val ex = new Exception("buh")
      val result = Source(1 to 100).map(x => if (x == 50) throw ex else x).detach.runWith(Sink.seq)
      intercept[Exception] {
        Await.result(result, 2.seconds)
      } should ===(ex)

    }

    "emit the last element when completed without demand" in {
      Source
        .single(42)
        .detach
        .runWith(TestSink.probe)
        .ensureSubscription()
        .expectNoMessage(500.millis)
        .requestNext() should ===(42)
    }

  }

}
