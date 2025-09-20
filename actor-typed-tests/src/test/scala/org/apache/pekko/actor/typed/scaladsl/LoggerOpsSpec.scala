/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.LoggingTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike

import org.slf4j.LoggerFactory

object LoggerOpsSpec {
  case class Value1(i: Int)
  case class Value2(i: Int)
  case class Value3(i: Int)
}

class LoggerOpsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {
  import LoggerOpsSpec._

  val log = LoggerFactory.getLogger(getClass)

  "LoggerOps" must {

    "provide extension method for 2 arguments" in {
      LoggingTestKit.info("[template a b]").expect {
        log.info2("[template {} {}]", "a", "b")
      }
      LoggingTestKit.info("[template a 2]").expect {
        log.info2("[template {} {}]", "a", 2)
      }
      LoggingTestKit.info("[template 1 2]").expect {
        log.info2("[template {} {}]", 1, 2)
      }
      LoggingTestKit.info("[template 1 b]").expect {
        log.info2("[template {} {}]", 1, "b")
      }
      LoggingTestKit.info("[template a Value2(2)]").expect {
        log.info2("[template {} {}]", "a", Value2(2))
      }
      LoggingTestKit.info("[template Value1(1) Value1(1)]").expect {
        log.info2("[template {} {}]", Value1(1), Value1(1))
      }
      LoggingTestKit.info("[template Value1(1) Value2(2)]").expect {
        log.info2("[template {} {}]", Value1(1), Value2(2))
      }
    }

    "provide extension method for vararg arguments" in {
      LoggingTestKit.info("[template a b c]").expect {
        log.infoN("[template {} {} {}]", "a", "b", "c")
      }
      LoggingTestKit.info("[template a b 3]").expect {
        log.infoN("[template {} {} {}]", "a", "b", 3)
      }
      LoggingTestKit.info("[template a 2 c]").expect {
        log.infoN("[template {} {} {}]", "a", 2, "c")
      }
      LoggingTestKit.info("[template 1 2 3]").expect {
        log.infoN("[template {} {} {}]", 1, 2, 3)
      }
      LoggingTestKit.info("[template 1 b c]").expect {
        log.infoN("[template {} {} {}]", 1, "b", "c")
      }
      LoggingTestKit.info("[template a Value2(2) Value3(3)]").expect {
        log.infoN("[template {} {} {}]", "a", Value2(2), Value3(3))
      }
      LoggingTestKit.info("[template Value1(1) Value1(1) Value1(1)]").expect {
        log.infoN("[template {} {} {}]", Value1(1), Value1(1), Value1(1))
      }
      LoggingTestKit.info("[template Value1(1) Value2(2) Value3(3)]").expect {
        log.infoN("[template {} {} {}]", Value1(1), Value2(2), Value3(3))
      }
    }
  }
}
