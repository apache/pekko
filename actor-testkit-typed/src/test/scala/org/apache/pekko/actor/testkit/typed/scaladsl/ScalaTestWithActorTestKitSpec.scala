/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import scala.concurrent.duration._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object ScalaTestWithActorTestKitSpec {
  class OverridesTestKit(base: ActorTestKit) extends ScalaTestWithActorTestKit(base) with AnyWordSpecLike {
    override val testKit: ActorTestKit =
      ActorTestKit(
        "ScalaTestWithActorTestKitSpec-overridden",
        ConfigFactory.parseString("pekko.actor.testkit.typed.default-timeout = 7s"))
  }
}

class ScalaTestWithActorTestKitSpec extends AnyWordSpec with Matchers {
  import ScalaTestWithActorTestKitSpec._

  "ScalaTestWithActorTestKit" should {

    "allow testKit to be overridden with a val" in {
      val base = ActorTestKit("ScalaTestWithActorTestKitSpec-base")
      try {
        val suite = new OverridesTestKit(base)
        try {
          suite.patience.timeout.totalNanos shouldBe suite.testKit.testKitSettings.DefaultTimeout.duration.toNanos
          suite.testKit.testKitSettings.DefaultTimeout.duration shouldBe
          suite.testKit.testKitSettings.dilated(7.seconds)
        } finally suite.testKit.shutdownTestKit()
      } finally base.shutdownTestKit()
    }
  }
}
