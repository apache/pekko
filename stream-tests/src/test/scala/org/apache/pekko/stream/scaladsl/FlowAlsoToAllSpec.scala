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

import org.apache.pekko
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.TestSink

class FlowAlsoToAllSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "An also to all" must {
    "publish elements to all its downstream" in {
      val (sub1, sink1) = TestSink.probe[Int].preMaterialize();
      val (sub2, sink2) = TestSink.probe[Int].preMaterialize();
      val (sub3, sink3) = TestSink.probe[Int].preMaterialize();
      Source.single(1).alsoToAll(sink1, sink2).runWith(sink3)
      sub1.expectSubscription().request(1)
      sub2.expectSubscription().request(1)
      sub3.expectSubscription().request(1)
      sub1.expectNext(1).expectComplete()
      sub2.expectNext(1).expectComplete()
      sub3.expectNext(1).expectComplete()
    }

    "publish elements to its only downstream" in {
      val (sub1, sink1) = TestSink.probe[Int].preMaterialize();
      Source.single(1).alsoToAll().runWith(sink1)
      sub1.expectSubscription().request(1)
      sub1.expectNext(1).expectComplete()
    }

  }
}
