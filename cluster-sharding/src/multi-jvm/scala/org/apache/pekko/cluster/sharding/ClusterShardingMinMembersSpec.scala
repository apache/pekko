/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.concurrent.duration._

import org.apache.pekko
import pekko.cluster.MemberStatus
import pekko.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import pekko.cluster.sharding.ShardRegion.{ ClusterShardingStats, GetClusterShardingStats }
import pekko.testkit._
import pekko.util.ccompat._

@ccompatUsedUntil213
abstract class ClusterShardingMinMembersSpecConfig(mode: String)
    extends MultiNodeClusterShardingConfig(
      mode,
      additionalConfig = s"""
        pekko.cluster.sharding.rebalance-interval = 120s #disable rebalance
        pekko.cluster.min-nr-of-members = 3
        """) {

  val first = role("first")
  val second = role("second")
  val third = role("third")

}

object PersistentClusterShardingMinMembersSpecConfig
    extends ClusterShardingMinMembersSpecConfig(ClusterShardingSettings.StateStoreModePersistence)
object DDataClusterShardingMinMembersSpecConfig
    extends ClusterShardingMinMembersSpecConfig(ClusterShardingSettings.StateStoreModeDData)

class PersistentClusterShardingMinMembersSpec
    extends ClusterShardingMinMembersSpec(PersistentClusterShardingMinMembersSpecConfig)
class DDataClusterShardingMinMembersSpec extends ClusterShardingMinMembersSpec(DDataClusterShardingMinMembersSpecConfig)

class PersistentClusterShardingMinMembersMultiJvmNode1 extends PersistentClusterShardingMinMembersSpec
class PersistentClusterShardingMinMembersMultiJvmNode2 extends PersistentClusterShardingMinMembersSpec
class PersistentClusterShardingMinMembersMultiJvmNode3 extends PersistentClusterShardingMinMembersSpec

class DDataClusterShardingMinMembersMultiJvmNode1 extends DDataClusterShardingMinMembersSpec
class DDataClusterShardingMinMembersMultiJvmNode2 extends DDataClusterShardingMinMembersSpec
class DDataClusterShardingMinMembersMultiJvmNode3 extends DDataClusterShardingMinMembersSpec

abstract class ClusterShardingMinMembersSpec(multiNodeConfig: ClusterShardingMinMembersSpecConfig)
    extends MultiNodeClusterShardingSpec(multiNodeConfig)
    with ImplicitSender {
  import MultiNodeClusterShardingSpec.ShardedEntity
  import multiNodeConfig._

  def startSharding(): Unit = {
    startSharding(
      system,
      typeName = "Entity",
      entityProps = TestActors.echoActorProps,
      extractEntityId = MultiNodeClusterShardingSpec.intExtractEntityId,
      extractShardId = MultiNodeClusterShardingSpec.intExtractShardId,
      allocationStrategy = ShardAllocationStrategy.leastShardAllocationStrategy(absoluteLimit = 2, relativeLimit = 1.0),
      handOffStopMessage = ShardedEntity.Stop)
  }

  lazy val region = ClusterSharding(system).shardRegion("Entity")

  s"Cluster with min-nr-of-members using sharding (${multiNodeConfig.mode})" must {

    "use all nodes" in within(30.seconds) {
      startPersistenceIfNeeded(startOn = first, setStoreOn = Seq(first, second, third))

      // the only test not asserting join status before starting to shard
      join(first, first, onJoinedRunOnFrom = startSharding(), assertNodeUp = false)
      join(second, first, onJoinedRunOnFrom = startSharding(), assertNodeUp = false)
      join(third, first, assertNodeUp = false)
      // wait with starting sharding on third
      within(remaining) {
        awaitAssert {
          cluster.state.members.size should ===(3)
          cluster.state.members.unsorted.map(_.status) should ===(Set(MemberStatus.Up))
        }
      }
      enterBarrier("all-up")

      runOn(first) {
        region ! 1
        // not allocated because third has not registered yet
        expectNoMessage(2.second)
      }
      enterBarrier("verified")

      runOn(third) {
        startSharding()
      }

      runOn(first) {
        // the 1 was sent above
        expectMsg(1)
        region ! 2
        expectMsg(2)
        region ! 3
        expectMsg(3)
      }
      enterBarrier("shards-allocated")

      region ! GetClusterShardingStats(remaining)
      val stats = expectMsgType[ClusterShardingStats]
      val firstAddress = node(first).address
      val secondAddress = node(second).address
      val thirdAddress = node(third).address
      withClue(stats) {
        stats.regions.keySet should ===(Set(firstAddress, secondAddress, thirdAddress))
        stats.regions(firstAddress).stats.valuesIterator.sum should ===(1)
      }
      enterBarrier("after-2")
    }

  }
}
