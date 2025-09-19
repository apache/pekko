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

package org.apache.pekko.cluster.sharding

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.sharding.Shard.GetShardStats
import pekko.cluster.sharding.Shard.ShardStats
import pekko.cluster.sharding.ShardRegion.StartEntity
import pekko.cluster.sharding.ShardRegion.StartEntityAck
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.WithLogCapturing

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object RememberEntitiesAndStartEntitySpec {
  class EntityActor extends Actor {
    override def receive: Receive = {
      case "give-me-shard" => sender() ! context.parent
      case msg             => sender() ! msg
    }
  }

  case class EntityEnvelope(entityId: Int, msg: Any)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) => (id.toString, payload)
    case _                           => throw new IllegalArgumentException()
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) => (id % 10).toString
    case StartEntity(id)       => (id.toInt % 10).toString
    case _                     => throw new IllegalArgumentException()
  }

  val config = ConfigFactory.parseString("""
      pekko.loglevel=DEBUG
      pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
      pekko.actor.provider = cluster
      pekko.remote.artery.canonical.port = 0
      pekko.remote.classic.netty.tcp.port = 0
      pekko.cluster.sharding.verbose-debug-logging = on
      pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
      # no leaks between test runs thank you
      pekko.cluster.sharding.distributed-data.durable.keys = []
    """.stripMargin)
}

// this test covers remember entities + StartEntity
class RememberEntitiesAndStartEntitySpec
    extends PekkoSpec(RememberEntitiesAndStartEntitySpec.config)
    with AnyWordSpecLike
    with ImplicitSender
    with WithLogCapturing {

  import RememberEntitiesAndStartEntitySpec._

  override def atStartup(): Unit = {
    // Form a one node cluster
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    awaitAssert(cluster.readView.members.count(_.status == MemberStatus.Up) should ===(1))
  }

  "Sharding" must {

    "remember entities started with StartEntity" in {
      val sharding = ClusterSharding(system).start(
        s"startEntity",
        Props[EntityActor](),
        ClusterShardingSettings(system).withRememberEntities(true),
        extractEntityId,
        extractShardId)

      sharding ! StartEntity("1")
      expectMsg(StartEntityAck("1", "1"))
      val shard = lastSender

      watch(shard)
      shard ! PoisonPill
      expectTerminated(shard)

      // trigger shard start by messaging other actor in it
      system.log.info("Starting shard again")
      // race condition between this message and region getting the termination message, we may need to retry
      val secondShardIncarnation = awaitAssert {
        sharding ! EntityEnvelope(11, "give-me-shard")
        expectMsgType[ActorRef]
      }

      awaitAssert {
        secondShardIncarnation ! GetShardStats
        // the remembered 1 and 11 which we just triggered start of
        expectMsg(ShardStats("1", 2))
      }
    }
  }

}
