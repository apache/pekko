/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.Await

import org.apache.pekko
import pekko.stream.StreamLimitReachedException
import pekko.stream.testkit.StreamSpec

class FlowLimitSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "Limit" must {
    "produce empty sequence when source is empty and n = 0" in {
      val input = Range(0, 0, 1)
      val n = input.length
      val future = Source(input).limit(n).grouped(Integer.MAX_VALUE).runWith(Sink.headOption)
      val result = Await.result(future, remainingOrDefault)
      result should be(None)
    }

    "produce output that is identical to the input when n = input.length" in {
      val input = 1 to 6
      val n = input.length
      val future = Source(input).limit(n).grouped(Integer.MAX_VALUE).runWith(Sink.head)
      val result = Await.result(future, remainingOrDefault)
      result should be(input.toSeq)
    }

    "produce output that is identical to the input when n > input.length" in {
      val input = 1 to 6
      val n = input.length + 2 // n > input.length
      val future = Source(input).limit(n).grouped(Integer.MAX_VALUE).runWith(Sink.head)
      val result = Await.result(future, remainingOrDefault)
      result should be(input.toSeq)
    }

    "produce n messages before throwing a StreamLimitReachedException when n < input.size" in {
      // TODO: check if it actually produces n messages
      val input = 1 to 6
      val n = input.length - 2 // n < input.length

      val future = Source(input).limit(n).grouped(Integer.MAX_VALUE).runWith(Sink.head)

      a[StreamLimitReachedException] shouldBe thrownBy {
        Await.result(future, remainingOrDefault)
      }
    }

    "throw a StreamLimitReachedException when n < 0" in {
      val input = 1 to 6
      val n = -1

      val future = Source(input).limit(n).grouped(Integer.MAX_VALUE).runWith(Sink.head)
      a[StreamLimitReachedException] shouldBe thrownBy {
        Await.result(future, remainingOrDefault)
      }
    }
  }
}
