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

package org.apache.pekko.actor.typed

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.actor.typed.scaladsl.Behaviors
import pekko.testkit.PekkoSpec

import scala.annotation.nowarn

@nowarn("msg=possible missing interpolator")
class ActorSystemSpec extends PekkoSpec {
  "ActorSystem" should {
    "not include username in toString" in {
      // Actor System toString is output to logs and we don't want env variable values appearing in logs
      val system = ActorSystem(Behaviors.empty[String], "config-test-system",
        ConfigFactory
          .parseString("""pekko.test.java.property.home = "${user.home}"""")
          .withFallback(PekkoSpec.testConf))
      try {
        val debugText = system.settings.toString
        val username = System.getProperty("user.name")
        val userHome = System.getProperty("user.home")
        (debugText should not).include(username)
        (debugText should not).include(userHome)
        debugText should include("<username>")
      } finally
        system.terminate()
    }
  }
}
