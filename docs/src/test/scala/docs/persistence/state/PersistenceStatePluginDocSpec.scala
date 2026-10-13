/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.persistence.state

import com.typesafe.config._
import docs.persistence

import org.apache.pekko
import pekko.Done
import pekko.actor.{ ActorSystem, ExtendedActorSystem }
//#plugin-imports
import pekko.persistence.state.scaladsl.DurableStateUpdateStore
import pekko.persistence.state.scaladsl.GetObjectResult
//#plugin-imports
import pekko.persistence.Persistence
import pekko.persistence.state.DurableStateStoreRegistry
import pekko.testkit.TestKit
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class PersistenceStatePluginDocSpec extends AnyWordSpec {

  val providerConfigJava =
    """
    //#plugin-config-java
    # Path to the state store plugin to be used
    pekko.persistence.state.plugin = "my-java-state-store"

    # My custom state store plugin
    my-java-state-store {
      # Class name of the plugin.
      class = "docs.persistence.state.MyJavaStateStoreProvider"
    }
    //#plugin-config-java
  """

  val providerConfig =
    """
    //#plugin-config-scala
    # Path to the state store plugin to be used
    pekko.persistence.state.plugin = "my-state-store"

    # My custom state store plugin
    my-state-store {
      # Class name of the plugin.
      class = "docs.persistence.state.MyStateStoreProvider"
    }
    //#plugin-config-scala
      """

  "it should work for scala" in {
    val system = ActorSystem("PersistenceStatePluginDocSpec", ConfigFactory.parseString(providerConfig))
    try {
      Persistence(system)
      DurableStateStoreRegistry(system).durableStateStoreFor[DurableStateUpdateStore[Any]]("my-state-store")
    } finally {
      TestKit.shutdownActorSystem(system, 10.seconds, false)
    }
  }

  "it should work for java" in {
    val system = ActorSystem("PersistenceStatePluginDocSpec", ConfigFactory.parseString(providerConfigJava))
    try {
      Persistence(system)
      DurableStateStoreRegistry(system).durableStateStoreFor[DurableStateUpdateStore[Any]]("my-java-state-store")
    } finally {
      TestKit.shutdownActorSystem(system, 10.seconds, false)
    }
  }
}
