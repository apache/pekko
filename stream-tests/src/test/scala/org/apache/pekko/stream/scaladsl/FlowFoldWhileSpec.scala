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

import org.apache.pekko
import pekko.stream.testkit.StreamSpec

class FlowFoldWhileSpec extends StreamSpec {

  "A Fold While" must {
    "can be used in the happy path" in {
      Source(1 to 10)
        .foldWhile(0)(_ < 10)(_ + _)
        .runWith(Sink.head)
        .futureValue shouldBe 10
    }

    "can be used to implement forAll" in {
      val f: Int => Boolean = any => any < 5
      Source(1 to 10)
        .foldWhile(true)(identity)(_ && f(_))
        .runWith(Sink.head)
        .futureValue shouldBe false
    }

    "can be used to implement exists" in {
      val f: Int => Boolean = any => any > 5
      Source(1 to 10)
        .foldWhile(false)(!_)(_ || f(_))
        .runWith(Sink.head)
        .futureValue shouldBe true
    }
  }

}
