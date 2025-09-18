/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding
import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ ActorRef, ActorSystem, Props }
import pekko.cluster.{ Cluster, MemberStatus }
import pekko.cluster.sharding.ShardRegion.CurrentRegions
import pekko.persistence.PersistentActor
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestProbe }

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Test migration from old persistent shard coordinator with remembered
 * entities to using a ddatabacked shard coordinator with an event sourced
 * replicated entity store.
 */
object PersistentShardingMigrationSpec {
  val config = ConfigFactory.parseString(s"""
       pekko.loglevel = INFO
       pekko.actor.provider = "cluster"
       pekko.remote.artery.canonical.port = 0 
       pekko.remote.classic.netty.tcp.port = 0
       pekko.cluster.sharding {
        remember-entities = on
        remember-entities-store = "eventsourced"

        # this forces the remembered entity store to use persistence
        # is is deprecated
        state-store-mode = "persistence"
       
        # make sure we test snapshots
        snapshot-after = 5
        
        verbose-debug-logging = on
        fail-on-invalid-entity-state-transition = on
        
        # Lots of sharding setup, make it quicker
        retry-interval = 500ms 
       }
       
       pekko.persistence.journal.plugin = "pekko.persistence.journal.leveldb"
       pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
       pekko.persistence.snapshot-store.local.dir = "target/PersistentShardingMigrationSpec-${UUID
      .randomUUID()
      .toString}"
       pekko.persistence.journal.leveldb {
         native = off
          dir = "target/journal-PersistentShardingMigrationSpec-${UUID.randomUUID()}"
      }
      """)

  val configForNewMode = ConfigFactory
    .parseString("""
       pekko.cluster.sharding {
        remember-entities = on
        remember-entities-store = "eventsourced"
        state-store-mode = "ddata"
       }
       
       pekko.persistence.journal.leveldb {
        event-adapters {
          coordinator-migration = "org.apache.pekko.cluster.sharding.OldCoordinatorStateMigrationEventAdapter"
        }

        event-adapter-bindings {
          "org.apache.pekko.cluster.sharding.ShardCoordinator$Internal$DomainEvent"        = coordinator-migration
        }
      }
       
      """)
    .withFallback(config)

  case class Message(id: Long)

  class PA extends PersistentActor {
    override def persistenceId: String = "pa-" + self.path.name
    override def receiveRecover: Receive = {
      case _ =>
    }
    override def receiveCommand: Receive = {
      case _ =>
        sender() ! "ack"
    }
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Message(id) => (id.toString, msg)
  }

  def extractShardId(probe: ActorRef): ShardRegion.ExtractShardId = {
    case Message(id)                 => id.toString
    case ShardRegion.StartEntity(id) =>
      // StartEntity is used by remembering entities feature
      probe ! id
      id
    case _ => throw new IllegalArgumentException()
  }
}

class PersistentShardingMigrationSpec extends PekkoSpec(PersistentShardingMigrationSpec.config) with ImplicitSender {

  import PersistentShardingMigrationSpec._

  "Migration" should {
    "allow migration of remembered shards and not allow going back" in {
      val typeName = "Migration"

      withSystem(config, typeName, "OldMode") { (_, region, _) =>
        assertRegionRegistrationComplete(region)
        region ! Message(1)
        expectMsg("ack")
        region ! Message(2)
        expectMsg("ack")
        region ! Message(3)
        expectMsg("ack")
      }

      withSystem(configForNewMode, typeName, "NewMode") { (system, region, rememberedEntitiesProbe) =>
        assertRegionRegistrationComplete(region)
        val probe = TestProbe()(system)
        region.tell(Message(1), probe.ref)
        probe.expectMsg("ack")
        Set(
          rememberedEntitiesProbe.expectMsgType[String],
          rememberedEntitiesProbe.expectMsgType[String],
          rememberedEntitiesProbe
            .expectMsgType[String]) shouldEqual Set("1", "2", "3") // 1-2 from the snapshot, 3 from a replayed message
        rememberedEntitiesProbe.expectNoMessage()
      }

      withSystem(config, typeName, "OldModeAfterMigration") { (system, region, _) =>
        val probe = TestProbe()(system)
        region.tell(Message(1), probe.ref)
        import scala.concurrent.duration._
        probe.expectNoMessage(5.seconds) // sharding should have failed to start
      }
    }
    "not allow going back to persistence mode based on a snapshot" in {
      val typeName = "Snapshots"
      withSystem(configForNewMode, typeName, "NewMode") { (system, region, _) =>
        val probe = TestProbe()(system)
        for (i <- 1 to 5) {
          region.tell(Message(i), probe.ref)
          probe.expectMsg("ack")
        }
      }

      withSystem(config, typeName, "OldModeShouldNotWork") { (system, region, _) =>
        val probe = TestProbe()(system)
        region.tell(Message(1), probe.ref)
        probe.expectNoMessage(1.seconds)
      }
    }

    def withSystem(config: Config, typeName: String, systemName: String)(
        f: (ActorSystem, ActorRef, TestProbe) => Unit) = {
      val system = ActorSystem(systemName, config)
      val cluster = Cluster(system)
      cluster.join(cluster.selfAddress)
      awaitAssert(cluster.selfMember.status shouldEqual MemberStatus.Up)
      try {
        val rememberedEntitiesProbe = TestProbe()(system)
        val region = ClusterSharding(system).start(
          typeName,
          Props(new PA()),
          extractEntityId,
          extractShardId(rememberedEntitiesProbe.ref))
        f(system, region, rememberedEntitiesProbe)
      } finally {
        Await.ready(system.terminate(), 20.seconds)
      }
    }

    def assertRegionRegistrationComplete(region: ActorRef): Unit = {
      awaitAssert {
        region ! ShardRegion.GetCurrentRegions
        expectMsgType[CurrentRegions].regions should have size 1
      }
    }
  }

}
