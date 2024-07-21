/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit

import scala.concurrent.duration._

import org.scalatest.exceptions.TestFailedException

class TestTimeSpec extends PekkoSpec(Map("pekko.test.timefactor" -> 2.0)) {

  "A TestKit" must {

    "correctly dilate times" taggedAs TimingTest in {
      1.second.dilated.toNanos should ===(1000000000L * testKitSettings.TestTimeFactor.toLong)

      val probe = TestProbe()
      val now = System.nanoTime
      intercept[AssertionError](probe.awaitCond(false, 1.second))
      val diff = System.nanoTime - now
      val target = (1000000000L * testKitSettings.TestTimeFactor).toLong
      diff should be >= target
      diff should be < (target + 1000000000L) // 1 s margin for GC
    }

    "awaitAssert must throw correctly" in {
      awaitAssert("foo" should ===("foo"))
      within(300.millis, 2.seconds) {
        intercept[TestFailedException] {
          awaitAssert("foo" should ===("bar"), 500.millis, 300.millis)
        }
      }
    }

    "throw if `remaining` is called outside of `within`" in {
      intercept[AssertionError] {
        remaining
      }
    }

  }
}
