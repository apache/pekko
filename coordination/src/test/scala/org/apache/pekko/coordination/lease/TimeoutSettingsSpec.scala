/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.coordination.lease

import scala.concurrent.duration._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.ConfigFactory

class TimeoutSettingsSpec extends AnyWordSpec with Matchers {
  private def conf(overrides: String): TimeoutSettings = {
    val c = ConfigFactory.parseString(overrides).withFallback(ConfigFactory.load())
    TimeoutSettings(c)
  }
  "TimeoutSettings" should {
    "default heartbeat-interval to heartbeat-timeout / 10" in {
      conf("""
          heartbeat-timeout=100s
          heartbeat-interval=""
          lease-operation-timeout=5s
        """).heartbeatInterval shouldEqual 10.second
    }

    "have a min of 5s for heartbeat-interval" in {
      conf("""
          heartbeat-timeout=40s
          heartbeat-interval=""
          lease-operation-timeout=5s
        """).heartbeatInterval shouldEqual 5.second
    }

    "allow overriding of heartbeat-interval" in {
      conf("""
          heartbeat-timeout=100s
          heartbeat-interval=20s
          lease-operation-timeout=5s
        """).heartbeatInterval shouldEqual 20.second
    }

    "not allow interval to be greater or equal to half the interval" in {
      intercept[IllegalArgumentException] {
        conf("""
          heartbeat-timeout=100s
          heartbeat-interval=50s
          lease-operation-timeout=5s
        """)
      }.getMessage shouldEqual "requirement failed: heartbeat-interval must be less than half heartbeat-timeout"

    }
  }

}
