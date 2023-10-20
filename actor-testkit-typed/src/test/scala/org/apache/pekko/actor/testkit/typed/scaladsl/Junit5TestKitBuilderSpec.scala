/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.actor.testkit.typed.scaladsl

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.Junit5TestKitBuilder
import org.scalatest.wordspec.AnyWordSpec

class Junit5TestKitBuilderSpec extends AnyWordSpec {

  "the Junit5TestKitBuilder" should {
    "create a Testkit with name hello" in {
      val actualTestKit = new Junit5TestKitBuilder().withName("hello").build()

      assertResult("hello")(actualTestKit.system.name)
    }
  }

  "the Junit5TestKitBuilder" should {
    "create a Testkit with the classname as name" in {
      val actualTestKit = new Junit5TestKitBuilder()
        .build()

      assertResult("Junit5TestKitBuilderSpec")(actualTestKit.system.name)
    }
  }

  "the Junit5TestKitBuilder" should {
    "create a Testkit with a custom config" in {

      val conf = ConfigFactory.load("application.conf")
      val actualTestKit = new Junit5TestKitBuilder()
        .withCustomConfig(conf)
        .build()
      assertResult("someValue")(actualTestKit.system.settings.config.getString("test.value"))
      assertResult("Junit5TestKitBuilderSpec")(actualTestKit.system.name)

    }
  }

  "the Junit5TestKitBuilder" should {
    "create a Testkit with a custom config and name" in {

      val conf = ConfigFactory.load("application.conf")
      val actualTestKit = new Junit5TestKitBuilder()
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

      val actualTestKit = new Junit5TestKitBuilder()
        .withSystem(system)
        .build()
      assertResult("AkkaQuickStart")(actualTestKit.system.name)
    }
  }

}
