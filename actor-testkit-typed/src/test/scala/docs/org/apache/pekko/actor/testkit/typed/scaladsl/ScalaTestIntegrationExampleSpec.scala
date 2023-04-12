/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.actor.testkit.typed.scaladsl

import scala.annotation.nowarn
import docs.org.apache.pekko.actor.testkit.typed.scaladsl.AsyncTestingExampleSpec.Echo
//#extend-multiple-times
//#log-capturing
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
//#scalatest-integration
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
//#scalatest-integration
//#log-capturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKitBase
//#scalatest-integration
import org.scalatest.wordspec.AnyWordSpecLike
//#scalatest-integration
//#extend-multiple-times

@nowarn
//#scalatest-integration
class ScalaTestIntegrationExampleSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Something" must {
    "behave correctly" in {
      val pinger = testKit.spawn(Echo(), "ping")
      val probe = testKit.createTestProbe[Echo.Pong]()
      pinger ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
    }
  }
}
//#scalatest-integration

//#log-capturing
class LogCapturingExampleSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "Something" must {
    "behave correctly" in {
      val pinger = testKit.spawn(Echo(), "ping")
      val probe = testKit.createTestProbe[Echo.Pong]()
      pinger ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
    }
  }
}
//#log-capturing

//#extend-multiple-times

trait ExtendTestMultipleTimes extends ScalaTestWithActorTestKitBase with AnyWordSpecLike with LogCapturing {
  "ScalaTestWithActorTestKitBase" must {
    "behave when extended in different ways" in {
      val pinger = testKit.spawn(Echo(), "ping")
      val probe = testKit.createTestProbe[Echo.Pong]()
      val message = this.getClass.getSimpleName
      pinger ! Echo.Ping(message, probe.ref)
      val returnedMessage = probe.expectMessage(Echo.Pong(message))
      returnedMessage.message.contains("ExtendTestMultipleTimes") shouldBe false
    }
  }

}

class TestWithOneImplementation extends ScalaTestWithActorTestKit with ExtendTestMultipleTimes
class TestWithAnotherImplementation extends ScalaTestWithActorTestKit with ExtendTestMultipleTimes
//#extend-multiple-times
