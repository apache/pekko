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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Actor, ActorRef, Props }
import pekko.cluster.MemberStatus
import pekko.serialization.jackson.CborSerializable
import pekko.testkit._

object ClusterShardingLeavingSpec {
  case class Ping(id: String) extends CborSerializable

  class Entity extends Actor {
    def receive = {
      case Ping(_) => sender() ! self
    }
  }

  case object GetLocations extends CborSerializable
  case class Locations(locations: Map[String, ActorRef]) extends CborSerializable

  class ShardLocations extends Actor {
    var locations: Locations = _
    def receive = {
      case GetLocations => sender() ! locations
      case l: Locations => locations = l
    }
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m @ Ping(id) => (id, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case Ping(id: String) => id.charAt(0).toString
    case _                => throw new IllegalArgumentException()
  }
}

abstract class ClusterShardingLeavingSpecConfig(mode: String)
    extends MultiNodeClusterShardingConfig(
      mode,
      loglevel = "DEBUG",
      additionalConfig =
        """
        pekko.cluster.sharding.verbose-debug-logging = on
        pekko.cluster.sharding.rebalance-interval = 1s # make rebalancing more likely to happen to test for https://github.com/akka/akka/issues/29093
        pekko.cluster.sharding.distributed-data.majority-min-cap = 1
        pekko.cluster.sharding.coordinator-state.write-majority-plus = 1
        pekko.cluster.sharding.coordinator-state.read-majority-plus = 1
      """) {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")
  val fifth = role("fifth")

}

object PersistentClusterShardingLeavingSpecConfig
    extends ClusterShardingLeavingSpecConfig(ClusterShardingSettings.StateStoreModePersistence)
object DDataClusterShardingLeavingSpecConfig
    extends ClusterShardingLeavingSpecConfig(ClusterShardingSettings.StateStoreModeDData)

class PersistentClusterShardingLeavingSpec
    extends ClusterShardingLeavingSpec(PersistentClusterShardingLeavingSpecConfig)
class DDataClusterShardingLeavingSpec extends ClusterShardingLeavingSpec(DDataClusterShardingLeavingSpecConfig)

class PersistentClusterShardingLeavingMultiJvmNode1 extends PersistentClusterShardingLeavingSpec
class PersistentClusterShardingLeavingMultiJvmNode2 extends PersistentClusterShardingLeavingSpec
class PersistentClusterShardingLeavingMultiJvmNode3 extends PersistentClusterShardingLeavingSpec
class PersistentClusterShardingLeavingMultiJvmNode4 extends PersistentClusterShardingLeavingSpec
class PersistentClusterShardingLeavingMultiJvmNode5 extends PersistentClusterShardingLeavingSpec

class DDataClusterShardingLeavingMultiJvmNode1 extends DDataClusterShardingLeavingSpec
class DDataClusterShardingLeavingMultiJvmNode2 extends DDataClusterShardingLeavingSpec
class DDataClusterShardingLeavingMultiJvmNode3 extends DDataClusterShardingLeavingSpec
class DDataClusterShardingLeavingMultiJvmNode4 extends DDataClusterShardingLeavingSpec
class DDataClusterShardingLeavingMultiJvmNode5 extends DDataClusterShardingLeavingSpec

abstract class ClusterShardingLeavingSpec(multiNodeConfig: ClusterShardingLeavingSpecConfig)
    extends MultiNodeClusterShardingSpec(multiNodeConfig)
    with ImplicitSender {
  import ClusterShardingLeavingSpec._
  import multiNodeConfig._

  def startSharding(): Unit = {
    startSharding(
      system,
      typeName = "Entity",
      entityProps = Props[Entity](),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)
  }

  lazy val region = ClusterSharding(system).shardRegion("Entity")

  s"Cluster sharding (${multiNodeConfig.mode}) with leaving member" must {

    "join cluster" in within(20.seconds) {
      startPersistenceIfNeeded(startOn = first, setStoreOn = roles)

      join(first, first, onJoinedRunOnFrom = startSharding())
      join(second, first, onJoinedRunOnFrom = startSharding(), assertNodeUp = false)
      join(third, first, onJoinedRunOnFrom = startSharding(), assertNodeUp = false)
      join(fourth, first, onJoinedRunOnFrom = startSharding(), assertNodeUp = false)
      join(fifth, first, onJoinedRunOnFrom = startSharding(), assertNodeUp = false)

      // all Up, everywhere before continuing
      awaitAssert {
        cluster.state.members.size should ===(roles.size)
        cluster.state.members.unsorted.map(_.status) should ===(Set(MemberStatus.Up))
      }

      enterBarrier("after-2")
    }

    "initialize shards" in {
      runOn(first) {
        val shardLocations = system.actorOf(Props[ShardLocations](), "shardLocations")
        val locations = (for (n <- 1 to 10) yield {
          val id = n.toString
          region ! Ping(id)
          id -> expectMsgType[ActorRef]
        }).toMap
        shardLocations ! Locations(locations)
        system.log.debug("Original locations: {}", locations)
      }
      enterBarrier("after-3")
    }

    "recover after leaving coordinator node" in {
      system.actorSelection(node(first) / "user" / "shardLocations") ! GetLocations
      val Locations(originalLocations) = expectMsgType[Locations]

      val numberOfNodesLeaving = 2
      val leavingRoles = roles.take(numberOfNodesLeaving)
      val leavingNodes = leavingRoles.map(address)
      val remainingRoles = roles.drop(numberOfNodesLeaving)

      runOn(roles.last) {
        leavingNodes.foreach { a =>
          cluster.leave(a)
        }
      }

      runOn(leavingRoles: _*) {
        watch(region)
        expectTerminated(region, 15.seconds)
      }
      // more stress by not having the barrier here

      runOn(remainingRoles: _*) {
        within(15.seconds) {
          awaitAssert {
            val probe = TestProbe()
            originalLocations.foreach {
              case (id, ref) =>
                region.tell(Ping(id), probe.ref)
                if (leavingNodes.contains(ref.path.address)) {
                  val newRef = probe.expectMsgType[ActorRef](1.second)
                  newRef should not be ref
                  system.log.debug("Moved [{}] from [{}] to [{}]", id, ref, newRef)
                } else
                  probe.expectMsg(1.second, ref) // should not move
            }
          }
        }
      }

      enterBarrier("after-4")
    }
  }
}
