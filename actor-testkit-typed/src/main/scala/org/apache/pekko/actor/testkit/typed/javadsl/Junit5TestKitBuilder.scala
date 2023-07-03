/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements; and to You under the Apache License, Version 2.0.
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 */

package org.apache.pekko.actor.testkit.typed.javadsl

import com.typesafe.config.Config
import org.apache.pekko.actor.testkit.typed.internal.TestKitUtils
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit.ApplicationTestConfig
import org.apache.pekko.actor.typed.ActorSystem

case class Junit5TestKitBuilder() {

  var system: Option[ActorSystem[_]] = None

  var customConfig: Config = ApplicationTestConfig

  var name: String = TestKitUtils.testNameFromCallStack(classOf[Junit5TestKitBuilder])

  def withSystem(system: ActorSystem[_]): Junit5TestKitBuilder = {
    this.system = Some(system)
    this
  }

  def withCustomConfig(customConfig: Config): Junit5TestKitBuilder = {
    this.customConfig = customConfig
    this
  }

  def withName(name: String): Junit5TestKitBuilder = {
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
