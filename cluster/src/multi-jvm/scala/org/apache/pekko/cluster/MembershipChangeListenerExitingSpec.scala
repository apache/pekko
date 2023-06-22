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

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.Deploy
import pekko.actor.Props
import pekko.cluster.MemberStatus._
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

object MembershipChangeListenerExitingMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(debugConfig(on = false).withFallback(MultiNodeClusterSpec.clusterConfigWithFailureDetectorPuppet))
}

class MembershipChangeListenerExitingMultiJvmNode1 extends MembershipChangeListenerExitingSpec
class MembershipChangeListenerExitingMultiJvmNode2 extends MembershipChangeListenerExitingSpec
class MembershipChangeListenerExitingMultiJvmNode3 extends MembershipChangeListenerExitingSpec

abstract class MembershipChangeListenerExitingSpec
    extends MultiNodeClusterSpec(MembershipChangeListenerExitingMultiJvmSpec) {

  import ClusterEvent._
  import MembershipChangeListenerExitingMultiJvmSpec._

  "A registered MembershipChangeListener" must {
    "be notified when new node is EXITING" taggedAs LongRunningTest in {

      awaitClusterUp(first, second, third)

      runOn(first) {
        enterBarrier("registered-listener")
        cluster.leave(second)
      }

      runOn(second) {
        val exitingLatch = TestLatch()
        val removedLatch = TestLatch()
        val secondAddress = address(second)
        cluster.subscribe(
          system.actorOf(Props(new Actor {
            def receive = {
              case state: CurrentClusterState =>
                if (state.members.exists(m => m.address == secondAddress && m.status == Exiting))
                  exitingLatch.countDown()
              case MemberExited(m) if m.address == secondAddress =>
                exitingLatch.countDown()
              case MemberRemoved(m, Exiting) if m.address == secondAddress =>
                removedLatch.countDown()
              case _ => // ignore
            }
          }).withDeploy(Deploy.local)),
          classOf[MemberEvent])
        enterBarrier("registered-listener")
        exitingLatch.await
        removedLatch.await
      }

      runOn(third) {
        val exitingLatch = TestLatch()
        val secondAddress = address(second)
        cluster.subscribe(
          system.actorOf(Props(new Actor {
            def receive = {
              case state: CurrentClusterState =>
                if (state.members.exists(m => m.address == secondAddress && m.status == Exiting))
                  exitingLatch.countDown()
              case MemberExited(m) if m.address == secondAddress =>
                exitingLatch.countDown()
              case _ => // ignore
            }
          }).withDeploy(Deploy.local)),
          classOf[MemberEvent])
        enterBarrier("registered-listener")
        exitingLatch.await
      }

      enterBarrier("finished")
    }
  }
}
