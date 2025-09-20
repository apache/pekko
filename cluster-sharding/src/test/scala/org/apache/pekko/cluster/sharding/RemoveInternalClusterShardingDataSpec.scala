/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

import org.apache.commons.io.FileUtils

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.sharding.RemoveInternalClusterShardingData.RemoveOnePersistenceId.Removals
import pekko.cluster.sharding.RemoveInternalClusterShardingData.RemoveOnePersistenceId.Result
import pekko.persistence.PersistentActor
import pekko.persistence.Recovery
import pekko.persistence.RecoveryCompleted
import pekko.persistence.SnapshotOffer
import pekko.persistence.SnapshotSelectionCriteria
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors.EchoActor
import pekko.testkit.WithLogCapturing

object RemoveInternalClusterShardingDataSpec {
  val config = """
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.persistence.journal.plugin = "pekko.persistence.journal.leveldb"
    pekko.persistence.journal.leveldb {
      native = off
      dir = "target/journal-RemoveInternalClusterShardingDataSpec"
    }
    pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
    pekko.persistence.snapshot-store.local.dir = "target/snapshots-RemoveInternalClusterShardingDataSpec"
    pekko.cluster.sharding.snapshot-after = 5
    pekko.cluster.sharding.state-store-mode = persistence
    pekko.cluster.sharding.keep-nr-of-batches = 0
    pekko.cluster.sharding.verbose-debug-logging = on
    """

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: Int => (msg.toString, msg)
    case _        => throw new IllegalArgumentException()
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: Int => (msg % 10).toString
    case _        => throw new IllegalArgumentException()
  }

  class HasSnapshots(override val persistenceId: String, replyTo: ActorRef) extends PersistentActor {

    var hasSnapshots = false

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, _) =>
        hasSnapshots = true
      case RecoveryCompleted =>
        replyTo ! hasSnapshots
        context.stop(self)

      case _ =>
    }

    override def receiveCommand: Receive = {
      case _ =>
    }
  }

  class HasEvents(override val persistenceId: String, replyTo: ActorRef) extends PersistentActor {

    var hasEvents = false

    override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.None)

    override def receiveRecover: Receive = {
      case _: ShardCoordinator.Internal.DomainEvent =>
        hasEvents = true
      case RecoveryCompleted =>
        replyTo ! hasEvents
        context.stop(self)
    }

    override def receiveCommand: Receive = {
      case _ =>
    }
  }

}

class RemoveInternalClusterShardingDataSpec
    extends PekkoSpec(RemoveInternalClusterShardingDataSpec.config)
    with ImplicitSender
    with WithLogCapturing {
  import RemoveInternalClusterShardingDataSpec._

  val storageLocations =
    List("pekko.persistence.journal.leveldb.dir", "pekko.persistence.snapshot-store.local.dir").map(s =>
      new File(system.settings.config.getString(s)))

  override protected def atStartup(): Unit = {
    storageLocations.foreach(dir => if (dir.exists) FileUtils.deleteDirectory(dir))
  }

  override protected def afterTermination(): Unit = {
    storageLocations.foreach(dir => if (dir.exists) FileUtils.deleteDirectory(dir))
  }

  // same persistenceId as is used by ShardCoordinator
  def persistenceId(typeName: String): String = s"/sharding/${typeName}Coordinator"

  def hasSnapshots(typeName: String): Boolean = {
    system.actorOf(Props(classOf[HasSnapshots], persistenceId(typeName), testActor))
    expectMsgType[Boolean]
  }

  def hasEvents(typeName: String): Boolean = {
    system.actorOf(Props(classOf[HasEvents], persistenceId(typeName), testActor))
    expectMsgType[Boolean]
  }

  "RemoveOnePersistenceId" must {
    "setup sharding" in {
      Cluster(system).join(Cluster(system).selfAddress)
      val settings = ClusterShardingSettings(system)
      ClusterSharding(system).start("type1", Props[EchoActor](), settings, extractEntityId, extractShardId)
      ClusterSharding(system).start("type2", Props[EchoActor](), settings, extractEntityId, extractShardId)
    }

    "work when no data" in within(10.seconds) {
      hasSnapshots("type1") should ===(false)
      hasEvents("type1") should ===(false)
      val rm = system.actorOf(
        RemoveInternalClusterShardingData.RemoveOnePersistenceId
          .props(journalPluginId = "", persistenceId("type1"), testActor))
      watch(rm)
      expectMsg(Result(Success(Removals(events = false, snapshots = false))))
      expectTerminated(rm)
    }

    "remove all events when no snapshot" in within(10.seconds) {
      val region = ClusterSharding(system).shardRegion("type1")
      (1 to 3).foreach(region ! _)
      receiveN(3).toSet should be((1 to 3).toSet)
      hasSnapshots("type1") should ===(false)
      hasEvents("type1") should ===(true)

      val rm = system.actorOf(
        RemoveInternalClusterShardingData.RemoveOnePersistenceId
          .props(journalPluginId = "", persistenceId("type1"), testActor))
      watch(rm)
      expectMsg(Result(Success(Removals(events = true, snapshots = false))))
      expectTerminated(rm)
      hasSnapshots("type1") should ===(false)
      hasEvents("type1") should ===(false)
    }

    "remove all events and snapshots" in within(10.seconds) {
      val region = ClusterSharding(system).shardRegion("type2")
      (1 to 10).foreach(region ! _)
      receiveN(10).toSet should be((1 to 10).toSet)
      awaitAssert {
        // theoretically it might take a while until snapshot is visible
        hasSnapshots("type2") should ===(true)
      }
      hasEvents("type2") should ===(true)

      val rm = system.actorOf(
        RemoveInternalClusterShardingData.RemoveOnePersistenceId
          .props(journalPluginId = "", persistenceId("type2"), testActor))
      watch(rm)
      expectMsg(Result(Success(Removals(events = true, snapshots = true))))
      expectTerminated(rm)
      hasSnapshots("type2") should ===(false)
      hasEvents("type2") should ===(false)
    }
  }

  "RemoveInternalClusterShardingData" must {
    val typeNames = List("type10", "type20", "type30")

    "setup sharding" in {
      Cluster(system).join(Cluster(system).selfAddress)
      val settings = ClusterShardingSettings(system)
      typeNames.foreach { typeName =>
        ClusterSharding(system).start(typeName, Props[EchoActor](), settings, extractEntityId, extractShardId)
      }
    }

    "remove all events and snapshots" in within(10.seconds) {
      typeNames.foreach { typeName =>
        val region = ClusterSharding(system).shardRegion(typeName)
        (1 to 10).foreach(region ! _)
        receiveN(10).toSet should be((1 to 10).toSet)
        awaitAssert {
          // theoretically it might take a while until snapshot is visible
          hasSnapshots(typeName) should ===(true)
        }
        hasEvents(typeName) should ===(true)
      }

      val result =
        RemoveInternalClusterShardingData.remove(system, journalPluginId = "", typeNames.toSet, remove2dot3Data = true)
      Await.ready(result, remaining)

      typeNames.foreach { typeName =>
        hasSnapshots(typeName) should ===(false)
        hasEvents(typeName) should ===(false)
      }
    }
  }
}
