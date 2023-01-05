/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Actor, ActorRef, ExtendedActorSystem, NoSerializationVerificationNeeded, PoisonPill, Props }
import pekko.cluster.ClusterSettings.DataCenter
import pekko.cluster.sharding.ShardCoordinator.Internal.ShardStopped
import pekko.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import pekko.cluster.sharding.ShardRegion.{ ExtractEntityId, ExtractShardId, HandOffStopper, Msg }
import pekko.testkit.WithLogCapturing
import pekko.testkit.{ PekkoSpec, TestProbe }

object ClusterShardingInternalsSpec {
  case class HandOffStopMessage() extends NoSerializationVerificationNeeded
  class EmptyHandlerActor extends Actor {
    override def receive: Receive = {
      case _ =>
    }

    override def postStop(): Unit = {
      super.postStop()
    }
  }
}

class ClusterShardingInternalsSpec extends PekkoSpec("""
    |pekko.actor.provider = cluster
    |pekko.remote.classic.netty.tcp.port = 0
    |pekko.remote.artery.canonical.port = 0
    |pekko.loglevel = DEBUG
    |pekko.cluster.sharding.verbose-debug-logging = on
    |pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
    |pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    |""".stripMargin) with WithLogCapturing {
  import ClusterShardingInternalsSpec._

  case class StartingProxy(
      typeName: String,
      role: Option[String],
      dataCenter: Option[DataCenter],
      extractEntityId: ExtractEntityId,
      extractShardId: ExtractShardId)

  val probe = TestProbe()

  val clusterSharding = new ClusterSharding(system.asInstanceOf[ExtendedActorSystem]) {
    override def startProxy(
        typeName: String,
        role: Option[String],
        dataCenter: Option[DataCenter],
        extractEntityId: ExtractEntityId,
        extractShardId: ExtractShardId): ActorRef = {
      probe.ref ! StartingProxy(typeName, role, dataCenter, extractEntityId, extractShardId)
      ActorRef.noSender
    }
  }

  "ClusterSharding" must {

    "start a region in proxy mode in case of node role mismatch" in {

      val settingsWithRole = ClusterShardingSettings(system).withRole("nonExistingRole")
      val typeName = "typeName"
      val extractEntityId: ExtractEntityId = { case msg: Msg => ("42", msg) }
      val extractShardId: ExtractShardId = _ => "37"

      clusterSharding.start(
        typeName = typeName,
        entityProps = Props.empty,
        settings = settingsWithRole,
        extractEntityId = extractEntityId,
        extractShardId = extractShardId,
        allocationStrategy = ShardAllocationStrategy.leastShardAllocationStrategy(3, 0.1),
        handOffStopMessage = PoisonPill)

      probe.expectMsg(StartingProxy(typeName, settingsWithRole.role, None, extractEntityId, extractShardId))
    }

    "stop entities from HandOffStopper even if the entity doesn't handle handOffStopMessage" in {
      val probe = TestProbe()
      val typeName = "typeName"
      val shard = "7"
      val emptyHandlerActor = system.actorOf(Props(new EmptyHandlerActor))
      val handOffStopper = system.actorOf(
        Props(new HandOffStopper(typeName, shard, probe.ref, Set(emptyHandlerActor), HandOffStopMessage, 10.millis)))

      watch(emptyHandlerActor)
      expectTerminated(emptyHandlerActor, 1.seconds)

      probe.expectMsg(1.seconds, ShardStopped(shard))
      probe.lastSender shouldEqual handOffStopper

      watch(handOffStopper)
      expectTerminated(handOffStopper, 1.seconds)
    }
  }
}
