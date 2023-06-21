package org.apache.pekko.actor.testkit.typed.javadsl

import com.typesafe.config.Config
import org.apache.pekko.actor.testkit.typed.internal.TestKitUtils
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit.ApplicationTestConfig
import org.apache.pekko.actor.typed.ActorSystem

 case class Junit5TestKitBuilder()  {

   var system: ActorSystem[_] = null

   var customConfig: Config = ApplicationTestConfig

   var name: String = TestKitUtils.testNameFromCallStack(classOf[Junit5TestKitBuilder])

  def withSystem(system: ActorSystem[_]): Junit5TestKitBuilder = {
    this.system = system
    this
  }

  def withCustomConfig(customConfig: Config): Junit5TestKitBuilder = {
    this.customConfig = customConfig
    this
  }

  def withName(name: String): Junit5TestKitBuilder = {
    this.name = name;
    this
  }

  def build(): ActorTestKit = {
    if (system != null) {
      return ActorTestKit.create(system)
    }
    ActorTestKit.create(name, customConfig)
  }

}

