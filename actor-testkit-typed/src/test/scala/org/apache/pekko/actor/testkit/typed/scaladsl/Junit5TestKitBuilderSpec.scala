/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package org.apache.pekko.actor.testkit.typed.scaladsl

import com.typesafe.config.ConfigFactory
import jdocs.org.apache.pekko.actor.testkit.typed.javadsl.GreeterMain
import org.apache.pekko.actor.testkit.typed.javadsl.Junit5TestKitBuilder
import org.apache.pekko.actor.typed.ActorSystem
import org.scalatest.wordspec.AnyWordSpec

class Junit5TestKitBuilderSpec extends AnyWordSpec {

  "the Junit5TestKitBuilder" should {
    "create a Testkit with name hello" in {
      val actualTestKit = Junit5TestKitBuilder()
        .withName("hello")
        .build()

      assertResult("hello")(actualTestKit.system.name)
    }
  }

  "the Junit5TestKitBuilder" should {
    "create a Testkit with the classname as name" in {
      val actualTestKit = Junit5TestKitBuilder()
        .build()

      assertResult("Junit5TestKitBuilderSpec")(actualTestKit.system.name)
    }
  }

  "the Junit5TestKitBuilder" should {
    "create a Testkit with a custom config" in {

      val conf = ConfigFactory.load("application.conf")
      val actualTestKit = Junit5TestKitBuilder()
        .withCustomConfig(conf)
        .build()
      assertResult("someValue")(actualTestKit.system.settings.config.getString("test.value"))
      assertResult("Junit5TestKitBuilderSpec")(actualTestKit.system.name)

    }
  }

  "the Junit5TestKitBuilder" should {
    "create a Testkit with a custom config and name" in {

      val conf = ConfigFactory.load("application.conf")
      val actualTestKit = Junit5TestKitBuilder()
        .withCustomConfig(conf)
        .withName("hello")
        .build()
      assertResult("someValue")(actualTestKit.system.settings.config.getString("test.value"))
      assertResult("hello")(actualTestKit.system.name)

    }
  }

  "the Junit5TestKitBuilder" should {
    "create a Testkit with a custom system" in {

      val system: ActorSystem[GreeterMain.SayHello] = ActorSystem(GreeterMain(), "AkkaQuickStart")

      val actualTestKit = Junit5TestKitBuilder()
        .withSystem(system)
        .build()
      assertResult("AkkaQuickStart")(actualTestKit.system.name)
    }
  }

}
