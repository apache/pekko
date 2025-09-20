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

package org.apache.pekko.cluster

import scala.collection.immutable
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Address
import pekko.cluster.ClusterEvent.CurrentClusterState
import pekko.cluster.ClusterEvent.MemberUp
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object DeterministicOldestWhenJoiningMultiJvmSpec extends MultiNodeConfig {
  val seed1 = role("seed1")
  val seed2 = role("seed2")
  val seed3 = role("seed3")

  commonConfig(
    debugConfig(on = false)
      .withFallback(ConfigFactory.parseString("""
    # not too quick to trigger problematic scenario more often
    pekko.cluster.leader-actions-interval = 2000 ms
    pekko.cluster.gossip-interval = 500 ms
    """))
      .withFallback(MultiNodeClusterSpec.clusterConfig))
}

class DeterministicOldestWhenJoiningMultiJvmNode1 extends DeterministicOldestWhenJoiningSpec
class DeterministicOldestWhenJoiningMultiJvmNode2 extends DeterministicOldestWhenJoiningSpec
class DeterministicOldestWhenJoiningMultiJvmNode3 extends DeterministicOldestWhenJoiningSpec

abstract class DeterministicOldestWhenJoiningSpec
    extends MultiNodeClusterSpec(DeterministicOldestWhenJoiningMultiJvmSpec) {

  import DeterministicOldestWhenJoiningMultiJvmSpec._

  // reverse order because that expose the bug in issue #18554
  def seedNodes: immutable.IndexedSeq[Address] =
    Vector(address(seed1), address(seed2), address(seed3)).sorted(Member.addressOrdering).reverse
  val roleByAddress = Map(address(seed1) -> seed1, address(seed2) -> seed2, address(seed3) -> seed3)

  "Joining a cluster" must {
    "result in deterministic oldest node" taggedAs LongRunningTest in {
      cluster.subscribe(testActor, classOf[MemberUp])
      expectMsgType[CurrentClusterState]

      runOn(roleByAddress(seedNodes.head)) {
        cluster.joinSeedNodes(seedNodes)
      }
      enterBarrier("first-seed-joined")

      runOn(roleByAddress(seedNodes(1)), roleByAddress(roleByAddress(seedNodes(2)))) {
        cluster.joinSeedNodes(seedNodes)
      }

      within(15.seconds) {
        val ups = List(expectMsgType[MemberUp], expectMsgType[MemberUp], expectMsgType[MemberUp])
        ups.map(_.member).sorted(Member.ageOrdering).head.address should ===(seedNodes.head)
      }

      enterBarrier("after-1")
    }

  }
}
