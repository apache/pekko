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

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.Deploy
import pekko.actor.Props
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

object MembershipChangeListenerUpMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(debugConfig(on = false).withFallback(MultiNodeClusterSpec.clusterConfigWithFailureDetectorPuppet))
}

class MembershipChangeListenerUpMultiJvmNode1 extends MembershipChangeListenerUpSpec
class MembershipChangeListenerUpMultiJvmNode2 extends MembershipChangeListenerUpSpec
class MembershipChangeListenerUpMultiJvmNode3 extends MembershipChangeListenerUpSpec

abstract class MembershipChangeListenerUpSpec extends MultiNodeClusterSpec(MembershipChangeListenerUpMultiJvmSpec) {

  import ClusterEvent._
  import MembershipChangeListenerUpMultiJvmSpec._

  "A set of connected cluster systems" must {

    "(when two nodes) after cluster convergence updates the membership table then all MembershipChangeListeners should be triggered" taggedAs LongRunningTest in {

      awaitClusterUp(first)

      runOn(first, second) {
        val latch = TestLatch()
        val expectedAddresses = Set(first, second).map(address)
        cluster.subscribe(
          system.actorOf(Props(new Actor {
            var members = Set.empty[Member]
            def receive = {
              case state: CurrentClusterState => members = state.members
              case MemberUp(m) =>
                members = members - m + m
                if (members.map(_.address) == expectedAddresses)
                  latch.countDown()
              case _ => // ignore
            }
          }).withDeploy(Deploy.local)), classOf[MemberEvent])
        enterBarrier("listener-1-registered")
        cluster.join(first)
        latch.await
      }

      runOn(third) {
        enterBarrier("listener-1-registered")
      }

      enterBarrier("after-1")
    }

    "(when three nodes) after cluster convergence updates the membership table then all MembershipChangeListeners should be triggered" taggedAs LongRunningTest in {

      val latch = TestLatch()
      val expectedAddresses = Set(first, second, third).map(address)
      cluster.subscribe(
        system.actorOf(Props(new Actor {
          var members = Set.empty[Member]
          def receive = {
            case state: CurrentClusterState => members = state.members
            case MemberUp(m) =>
              members = members - m + m
              if (members.map(_.address) == expectedAddresses)
                latch.countDown()
            case _ => // ignore
          }
        }).withDeploy(Deploy.local)), classOf[MemberEvent])
      enterBarrier("listener-2-registered")

      runOn(third) {
        cluster.join(first)
      }

      latch.await

      enterBarrier("after-2")
    }
  }
}
