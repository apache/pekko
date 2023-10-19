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

package org.apache.pekko.actor.testkit.typed.javadsl

import org.apache.pekko
import com.typesafe.config.Config
import pekko.actor.testkit.typed.internal.TestKitUtils
import pekko.actor.testkit.typed.scaladsl.ActorTestKit.ApplicationTestConfig
import pekko.actor.typed.ActorSystem

final class JUnit5TestKitBuilder() {

  var system: Option[ActorSystem[_]] = None

  var customConfig: Config = ApplicationTestConfig

  var name: String = TestKitUtils.testNameFromCallStack(classOf[JUnit5TestKitBuilder])

  def withSystem(system: ActorSystem[_]): JUnit5TestKitBuilder = {
    this.system = Some(system)
    this
  }

  def withCustomConfig(customConfig: Config): JUnit5TestKitBuilder = {
    this.customConfig = customConfig
    this
  }

  def withName(name: String): JUnit5TestKitBuilder = {
    this.name = name
    this
  }

  def build(): ActorTestKit = {
    if (system.isDefined) {
      return ActorTestKit.create(system.get)
    }
    ActorTestKit.create(name, customConfig)
  }

}