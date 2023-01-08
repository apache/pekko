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

package org.apache.pekko.cluster

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.Deploy
import pekko.actor.Props
import pekko.cluster.MemberStatus._
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

object LeaderLeavingMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(
    debugConfig(on = false)
      .withFallback(ConfigFactory.parseString("""
      pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
      pekko.cluster.testkit.auto-down-unreachable-after = 0s"""))
      .withFallback(MultiNodeClusterSpec.clusterConfigWithFailureDetectorPuppet))
}

class LeaderLeavingMultiJvmNode1 extends LeaderLeavingSpec
class LeaderLeavingMultiJvmNode2 extends LeaderLeavingSpec
class LeaderLeavingMultiJvmNode3 extends LeaderLeavingSpec

abstract class LeaderLeavingSpec extends MultiNodeClusterSpec(LeaderLeavingMultiJvmSpec) {

  import ClusterEvent._
  import LeaderLeavingMultiJvmSpec._

  "A LEADER that is LEAVING" must {

    "be moved to LEAVING, then to EXITING, then to REMOVED, then be shut down and then a new LEADER should be elected" taggedAs LongRunningTest in {

      awaitClusterUp(first, second, third)

      val oldLeaderAddress = clusterView.leader.get

      within(30.seconds) {

        if (clusterView.isLeader) {

          enterBarrier("registered-listener")

          cluster.leave(oldLeaderAddress)
          enterBarrier("leader-left")

          // verify that the LEADER is shut down
          awaitCond(cluster.isTerminated)
          enterBarrier("leader-shutdown")

        } else {

          val exitingLatch = TestLatch()

          cluster.subscribe(
            system.actorOf(Props(new Actor {
              def receive = {
                case state: CurrentClusterState =>
                  if (state.members.exists(m => m.address == oldLeaderAddress && m.status == Exiting))
                    exitingLatch.countDown()
                case MemberExited(m) if m.address == oldLeaderAddress => exitingLatch.countDown()
                case _                                                => // ignore
              }
            }).withDeploy(Deploy.local)), classOf[MemberEvent])
          enterBarrier("registered-listener")

          enterBarrier("leader-left")

          // verify that the LEADER is EXITING
          exitingLatch.await

          enterBarrier("leader-shutdown")
          markNodeAsUnavailable(oldLeaderAddress)

          // verify that the LEADER is no longer part of the 'members' set
          awaitAssert(clusterView.members.map(_.address) should not contain oldLeaderAddress)

          // verify that the LEADER is not part of the 'unreachable' set
          awaitAssert(clusterView.unreachableMembers.map(_.address) should not contain oldLeaderAddress)

          // verify that we have a new LEADER
          awaitAssert(clusterView.leader should not be oldLeaderAddress)
        }

        enterBarrier("finished")
      }
    }
  }
}
