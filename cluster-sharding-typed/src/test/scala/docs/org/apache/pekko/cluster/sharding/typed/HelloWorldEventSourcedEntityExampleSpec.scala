/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.typed.Cluster
import pekko.cluster.typed.Join
import pekko.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

object HelloWorldEventSourcedEntityExampleSpec {
  val config = ConfigFactory.parseString("""
      pekko.actor.provider = cluster

      pekko.remote.classic.netty.tcp.port = 0
      pekko.remote.artery.canonical.port = 0
      pekko.remote.artery.canonical.hostname = 127.0.0.1

      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.journal.inmem.test-serialization = on
    """)
}

class HelloWorldEventSourcedEntityExampleSpec
    extends ScalaTestWithActorTestKit(HelloWorldEventSourcedEntityExampleSpec.config)
    with AnyWordSpecLike
    with LogCapturing {
  import HelloWorldPersistentEntityExample.HelloWorld
  import HelloWorldPersistentEntityExample.HelloWorld._

  val sharding = ClusterSharding(system)

  override def beforeAll(): Unit = {
    super.beforeAll()
    Cluster(system).manager ! Join(Cluster(system).selfMember.address)

    sharding.init(Entity(HelloWorld.TypeKey) { entityContext =>
      HelloWorld(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
    })
  }

  "HelloWorld example" must {

    "sayHello" in {
      val probe = createTestProbe[Greeting]()
      val ref = ClusterSharding(system).entityRefFor(HelloWorld.TypeKey, "1")
      ref ! Greet("Alice")(probe.ref)
      probe.expectMessage(Greeting("Alice", 1))
      ref ! Greet("Bob")(probe.ref)
      probe.expectMessage(Greeting("Bob", 2))
    }

    "verifySerialization" in {
      val probe = createTestProbe[Greeting]()
      serializationTestKit.verifySerialization(Greet("Alice")(probe.ref))
      serializationTestKit.verifySerialization(Greeting("Alice", 1))
      serializationTestKit.verifySerialization(KnownPeople(Set.empty).add("Alice").add("Bob"))
    }

  }
}
