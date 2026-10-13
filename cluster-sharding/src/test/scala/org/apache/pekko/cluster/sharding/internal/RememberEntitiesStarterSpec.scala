/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.internal

import scala.concurrent.duration._

import org.apache.pekko
import pekko.cluster.sharding.ClusterShardingSettings
import pekko.cluster.sharding.Shard
import pekko.cluster.sharding.ShardRegion
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe

import com.typesafe.config.ConfigFactory

class RememberEntitiesStarterSpec extends PekkoSpec {

  var shardIdCounter = 1
  def nextShardId(): ShardId = {
    val id = s"ShardId$shardIdCounter"
    shardIdCounter += 1
    id
  }

  "The RememberEntitiesStarter" must {
    "try start all entities directly with entity-recovery-strategy = all (default)" in {
      val regionProbe = TestProbe()
      val shardProbe = TestProbe()
      val shardId = nextShardId()

      val defaultSettings = ClusterShardingSettings(system)

      val rememberEntityStarter = system.actorOf(
        RememberEntityStarter.props(
          regionProbe.ref,
          shardProbe.ref,
          shardId,
          Set("1", "2", "3"),
          isConstantStrategy = false,
          defaultSettings))

      watch(rememberEntityStarter)
      val startedEntityIds = (1 to 3).map { _ =>
        val start = regionProbe.expectMsgType[ShardRegion.StartEntity]
        regionProbe.lastSender ! ShardRegion.StartEntityAck(start.entityId, shardId)
        start.entityId
      }.toSet
      startedEntityIds should ===(Set("1", "2", "3"))

      // the starter should then stop itself, not sending anything more to the shard or region
      expectTerminated(rememberEntityStarter)
      shardProbe.expectNoMessage()
      regionProbe.expectNoMessage()
    }

    "retry start all entities with no ack with entity-recovery-strategy = all (default)" in {
      val regionProbe = TestProbe()
      val shardProbe = TestProbe()
      val shardId = nextShardId()

      val customSettings = ClusterShardingSettings(
        ConfigFactory
          .parseString(
            // the restarter somewhat surprisingly uses this for no-ack-retry. Tune it down to speed up test
            """
             retry-interval = 1 second
            """)
          .withFallback(system.settings.config.getConfig("pekko.cluster.sharding")))

      val rememberEntityStarter = system.actorOf(
        RememberEntityStarter.props(
          regionProbe.ref,
          shardProbe.ref,
          shardId,
          Set("1", "2", "3"),
          isConstantStrategy = false,
          customSettings))

      watch(rememberEntityStarter)
      (1 to 3).foreach { _ =>
        regionProbe.expectMsgType[ShardRegion.StartEntity]
      }
      val startedOnSecondTry = (1 to 3).map { _ =>
        val start = regionProbe.expectMsgType[ShardRegion.StartEntity]
        regionProbe.lastSender ! ShardRegion.StartEntityAck(start.entityId, shardId)
        start.entityId
      }.toSet
      startedOnSecondTry should ===(Set("1", "2", "3"))

      // should stop itself, not sending anything to the shard
      expectTerminated(rememberEntityStarter)
      shardProbe.expectNoMessage()
    }

    "inform the shard when entities has been reallocated to different shard id" in {
      val regionProbe = TestProbe()
      val shardProbe = TestProbe()
      val shardId = nextShardId()

      val customSettings = ClusterShardingSettings(
        ConfigFactory
          .parseString(
            // the restarter somewhat surprisingly uses this for no-ack-retry. Tune it down to speed up test
            """
             retry-interval = 1 second
            """)
          .withFallback(system.settings.config.getConfig("pekko.cluster.sharding")))

      val rememberEntityStarter = system.actorOf(
        RememberEntityStarter.props(
          regionProbe.ref,
          shardProbe.ref,
          shardId,
          Set("1", "2", "3"),
          isConstantStrategy = false,
          customSettings))

      watch(rememberEntityStarter)
      val start1 = regionProbe.expectMsgType[ShardRegion.StartEntity]
      regionProbe.lastSender ! ShardRegion.StartEntityAck(start1.entityId, shardId) // keep on current shard

      val start2 = regionProbe.expectMsgType[ShardRegion.StartEntity]
      regionProbe.lastSender ! ShardRegion.StartEntityAck(start2.entityId, shardId = "Relocated1")

      val start3 = regionProbe.expectMsgType[ShardRegion.StartEntity]
      regionProbe.lastSender ! ShardRegion.StartEntityAck(start3.entityId, shardId = "Relocated2")

      shardProbe.expectMsg(Shard.EntitiesMovedToOtherShard(Set("2", "3")))
      expectTerminated(rememberEntityStarter)
    }

    "try start all entities in a throttled way with entity-recovery-strategy = constant" in {
      val regionProbe = TestProbe()
      val shardProbe = TestProbe()
      val shardId = nextShardId()

      val customSettings = ClusterShardingSettings(
        ConfigFactory
          .parseString(
            // slow constant restart
            """
             entity-recovery-strategy = constant
             entity-recovery-constant-rate-strategy {
               frequency = 2 s
               number-of-entities = 2
             }
             retry-interval = 1 second
            """)
          .withFallback(system.settings.config.getConfig("pekko.cluster.sharding")))

      val rememberEntityStarter = system.actorOf(
        RememberEntityStarter.props(
          regionProbe.ref,
          shardProbe.ref,
          shardId,
          Set("1", "2", "3", "4", "5"),
          isConstantStrategy = true,
          customSettings))

      def recieveStartAndAck() = {
        val start = regionProbe.expectMsgType[ShardRegion.StartEntity]
        regionProbe.lastSender ! ShardRegion.StartEntityAck(start.entityId, shardId)
      }

      watch(rememberEntityStarter)
      // first batch should be immediate
      recieveStartAndAck()
      recieveStartAndAck()
      // second batch holding off (with some room for unstable test env)
      regionProbe.expectNoMessage(600.millis)

      // second batch should be immediate
      recieveStartAndAck()
      recieveStartAndAck()
      // third batch holding off
      regionProbe.expectNoMessage(600.millis)

      recieveStartAndAck()

      // the starter should then stop itself, not sending anything more to the shard or region
      expectTerminated(rememberEntityStarter)
      shardProbe.expectNoMessage()
      regionProbe.expectNoMessage()
    }

  }

  "The RememberEntityStarterManager" must {
    "start entities for all shards immediately with entity-recovery-strategy = all (default)" in {
      val regionProbe = TestProbe()
      val shardProbe1 = TestProbe()
      val shardProbe2 = TestProbe()
      val shardId1 = nextShardId()
      val shardId2 = nextShardId()

      val defaultSettings = ClusterShardingSettings(system)

      val manager = system.actorOf(RememberEntityStarterManager.props(regionProbe.ref, defaultSettings))

      manager ! RememberEntityStarterManager.StartEntities(shardProbe1.ref, shardId1, Set("1", "2"))
      manager ! RememberEntityStarterManager.StartEntities(shardProbe2.ref, shardId2, Set("3", "4"))

      // both shards should be started immediately (all strategy, no queuing)
      val startedEntityIds = (1 to 4).map { _ =>
        val start = regionProbe.expectMsgType[ShardRegion.StartEntity]
        regionProbe.lastSender ! ShardRegion.StartEntityAck(start.entityId,
          start.entityId match {
            case "1" | "2" => shardId1
            case _         => shardId2
          })
        start.entityId
      }.toSet
      startedEntityIds should ===(Set("1", "2", "3", "4"))
    }

    "throttle entity starting across shards with entity-recovery-strategy = constant" in {
      val regionProbe = TestProbe()
      val shard1Probe = TestProbe()
      val shard2Probe = TestProbe()
      val shardId1 = nextShardId()
      val shardId2 = nextShardId()

      val customSettings = ClusterShardingSettings(
        ConfigFactory
          .parseString(
            """
             entity-recovery-strategy = constant
             entity-recovery-constant-rate-strategy {
               frequency = 2 s
               number-of-entities = 2
             }
             retry-interval = 1 second
            """)
          .withFallback(system.settings.config.getConfig("pekko.cluster.sharding")))

      val manager = system.actorOf(RememberEntityStarterManager.props(regionProbe.ref, customSettings))

      manager ! RememberEntityStarterManager.StartEntities(shard1Probe.ref, shardId1, Set("1", "2", "3", "4", "5"))
      manager ! RememberEntityStarterManager.StartEntities(shard2Probe.ref, shardId2, Set("6", "7", "8"))

      import pekko.cluster.sharding.ShardRegion.EntityId

      def receiveStartAndAck(): EntityId = {
        val start = regionProbe.expectMsgType[ShardRegion.StartEntity]
        val shardId = if (start.entityId.toInt <= 5) shardId1 else shardId2
        regionProbe.lastSender ! ShardRegion.StartEntityAck(start.entityId, shardId)
        start.entityId
      }

      var startedEntityIds = Set.empty[EntityId]

      // first batch for shard1 should be immediate
      startedEntityIds += receiveStartAndAck()
      startedEntityIds += receiveStartAndAck()

      // second batch holding off
      regionProbe.expectNoMessage(600.millis)
      startedEntityIds += receiveStartAndAck()
      startedEntityIds += receiveStartAndAck()

      // third batch holding off
      regionProbe.expectNoMessage(600.millis)
      startedEntityIds += receiveStartAndAck()

      startedEntityIds should ===(Set("1", "2", "3", "4", "5"))

      // now the second StartEntities for shard2 — still throttled after delay
      regionProbe.expectNoMessage(600.millis)
      startedEntityIds += receiveStartAndAck()
      startedEntityIds += receiveStartAndAck()

      regionProbe.expectNoMessage(600.millis)
      startedEntityIds += receiveStartAndAck()

      startedEntityIds should ===(Set("1", "2", "3", "4", "5", "6", "7", "8"))
    }
  }
}
