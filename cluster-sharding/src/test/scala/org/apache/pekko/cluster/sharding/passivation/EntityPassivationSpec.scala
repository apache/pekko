/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.passivation

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.sharding.ClusterSharding
import pekko.cluster.sharding.ClusterShardingSettings
import pekko.cluster.sharding.ShardRegion
import pekko.testkit.WithLogCapturing
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

object EntityPassivationSpec {

  val config: Config = ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.cluster.sharding.verbose-debug-logging = on
    pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
    """)

  val disabledConfig: Config = ConfigFactory.parseString("""
    pekko.cluster.sharding {
      passivation {
        strategy = none
      }
    }
    """).withFallback(config)

  object Entity {
    case object Stop
    case object ManuallyPassivate
    case class Envelope(shard: Int, id: Int, message: Any)
    case class Received(id: String, message: Any, nanoTime: Long)

    def props(probes: Map[String, ActorRef]) = Props(new Entity(probes))
  }

  class Entity(probes: Map[String, ActorRef]) extends Actor {
    def id = context.self.path.name

    def received(message: Any) = probes(id) ! Entity.Received(id, message, System.nanoTime())

    def receive = {
      case Entity.Stop =>
        received(Entity.Stop)
        context.stop(self)
      case Entity.ManuallyPassivate =>
        received(Entity.ManuallyPassivate)
        context.parent ! ShardRegion.Passivate(Entity.Stop)
      case msg => received(msg)
    }
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case Entity.Envelope(_, id, message) => (id.toString, message)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case Entity.Envelope(shard, _, _) => shard.toString
    case _                            => throw new IllegalArgumentException
  }
}

abstract class AbstractEntityPassivationSpec(config: Config, expectedEntities: Int)
    extends PekkoSpec(config)
    with Eventually
    with WithLogCapturing {

  import EntityPassivationSpec._

  val settings: ClusterShardingSettings = ClusterShardingSettings(system)
  val configuredIdleTimeout: FiniteDuration =
    settings.passivationStrategySettings.idleEntitySettings.fold(Duration.Zero)(_.timeout)
  val configuredActiveEntityLimit: Int = settings.passivationStrategySettings.activeEntityLimit.getOrElse(0)

  val probes: Map[Int, TestProbe] = (1 to expectedEntities).map(id => id -> TestProbe()).toMap
  val probeRefs: Map[String, ActorRef] = probes.map { case (id, probe) => id.toString -> probe.ref }
  val stateProbe: TestProbe = TestProbe()

  def expectReceived(id: Int, message: Any, within: FiniteDuration = patience.timeout): Entity.Received = {
    val received = probes(id).expectMsgType[Entity.Received](within)
    received.message shouldBe message
    received
  }

  def expectNoMessage(id: Int, within: FiniteDuration): Unit =
    probes(id).expectNoMessage(within)

  def getState(region: ActorRef): ShardRegion.CurrentShardRegionState = {
    region.tell(ShardRegion.GetShardRegionState, stateProbe.ref)
    stateProbe.expectMsgType[ShardRegion.CurrentShardRegionState]
  }

  def expectState(region: ActorRef)(expectedShards: (Int, Iterable[Int])*): Unit =
    eventually {
      getState(region).shards should contain theSameElementsAs expectedShards.map {
        case (shardId, entityIds) => ShardRegion.ShardState(shardId.toString, entityIds.map(_.toString).toSet)
      }
    }

  def start(): ActorRef = {
    // single node cluster
    Cluster(system).join(Cluster(system).selfAddress)
    ClusterSharding(system).start(
      "myType",
      EntityPassivationSpec.Entity.props(probeRefs),
      settings,
      extractEntityId,
      extractShardId,
      ClusterSharding(system).defaultShardAllocationStrategy(settings),
      Entity.Stop)
  }
}

class DisabledEntityPassivationSpec
    extends AbstractEntityPassivationSpec(EntityPassivationSpec.disabledConfig, expectedEntities = 1) {

  import EntityPassivationSpec.Entity.Envelope

  "Passivation of idle entities" must {
    "not passivate when passivation is disabled" in {
      settings.passivationStrategy shouldBe ClusterShardingSettings.NoPassivationStrategy
      val region = start()
      region ! Envelope(shard = 1, id = 1, message = "A")
      expectReceived(id = 1, message = "A")
      expectNoMessage(id = 1, 1.second)
    }
  }
}
