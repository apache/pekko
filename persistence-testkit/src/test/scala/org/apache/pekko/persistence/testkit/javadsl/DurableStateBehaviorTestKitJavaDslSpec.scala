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

package org.apache.pekko.persistence.testkit.javadsl

import org.apache.pekko.actor.testkit.typed.scaladsl.LogCapturing
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike

class DurableStateBehaviorTestKitJavaDslSpec
    extends ScalaTestWithActorTestKit(DurableStateBehaviorTestKit.config)
    with AnyWordSpecLike
    with LogCapturing {

  "The Java DurableStateBehaviorTestKit API" must {
    "run commands, return replies, expose state, and restart" in {
      DurableStateBehaviorTestKitApiTest.run(system)
    }

    "clear the state" in {
      DurableStateBehaviorTestKitApiTest.clear(system)
    }
  }
}
