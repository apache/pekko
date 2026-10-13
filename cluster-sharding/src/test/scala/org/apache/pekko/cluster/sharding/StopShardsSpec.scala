/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Props
import pekko.actor.Terminated
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.sharding.ShardCoordinator.Internal.ShardStopped
import pekko.cluster.sharding.ShardRegion.CurrentRegions
import pekko.cluster.sharding.ShardRegion.GetCurrentRegions
import pekko.testkit.DeadLettersFilter
import pekko.testkit.PekkoSpec
import pekko.testkit.TestEvent.Mute
import pekko.testkit.TestProbe
import pekko.testkit.WithLogCapturing

import com.typesafe.config.ConfigFactory

object StopShardsSpec {

  def config =
    ConfigFactory.parseString("""
        pekko.loglevel = DEBUG
        pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
        pekko.actor.provider = "cluster"
        pekko.remote.artery.canonical.port = 0
        pekko.remote.classic.netty.tcp.port = 0
        pekko.test.single-expect-default = 5 s
        pekko.cluster.sharding.distributed-data.durable.keys = []
        pekko.cluster.sharding.remember-entities = off
        pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
        pekko.cluster.jmx.enabled = off
        pekko.cluster.sharding.verbose-debug-logging = on
        pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
        pekko.remote.artery.canonical.hostname = "127.0.0.1"
        """)

  val shardTypeName = "stopping-entities"

  val numberOfShards = 3

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: Int => (msg.toString, msg)
    case _        => throw new IllegalArgumentException()
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: Int                    => (msg % 10).toString
    case ShardRegion.StartEntity(id) => (id.toLong % numberOfShards).toString
    case _                           => throw new IllegalArgumentException()
  }

  class EntityActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case _ =>
        log.debug("ping")
        sender() ! context.self
    }
  }
}

class StopShardsSpec extends PekkoSpec(StopShardsSpec.config) with WithLogCapturing {
  import StopShardsSpec._

  // mute logging of deadLetters
  system.eventStream.publish(Mute(DeadLettersFilter[Any]))

  private val sysA = system
  private val sysB = ActorSystem(system.name, system.settings.config)

  private val pA = TestProbe()(sysA)
  private val pB = TestProbe()(sysB)

  private val regionA = startShardRegion(sysA)
  private val regionB = startShardRegion(sysB)

  override protected def beforeTermination(): Unit = {
    shutdown(sysB)
    super.beforeTermination()
  }

  "The StopShards command" must {

    "form a cluster" in {
      Cluster(sysA).join(Cluster(sysA).selfAddress) // coordinator on A
      awaitAssert(Cluster(sysA).selfMember.status shouldEqual MemberStatus.Up, 3.seconds)

      Cluster(sysB).join(Cluster(sysA).selfAddress)
      awaitAssert(Cluster(sysB).selfMember.status shouldEqual MemberStatus.Up, 3.seconds)

      // wait for all regions to be registered
      pA.awaitAssert({
          regionA.tell(GetCurrentRegions, pA.ref)
          pA.expectMsgType[CurrentRegions].regions should have size 2
        }, 10.seconds)
    }

    "start entities in a few shards, then stop the shards" in {

      val allShards = (1 to 10).map { i =>
        regionA.tell(i, pA.ref)
        val entityRef = pA.expectMsgType[ActorRef]
        pA.watch(entityRef) // so we can verify terminated later
        extractShardId(i)
      }.toSet

      regionB.tell(ShardCoordinator.Internal.StopShards(allShards), pB.ref)
      (1 to allShards.size).foreach { _ =>
        // one ack for each shard stopped
        pB.expectMsgType[ShardStopped].shard
      }

      // all entities stopped
      (1 to 10).foreach(_ => pA.expectMsgType[Terminated])
    }
  }

  def startShardRegion(sys: ActorSystem): ActorRef =
    ClusterSharding(sys).start(
      shardTypeName,
      Props[EntityActor](),
      ClusterShardingSettings(system),
      extractEntityId,
      extractShardId)

}
