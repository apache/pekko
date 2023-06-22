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

object NodeLeavingAndExitingMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(debugConfig(on = false).withFallback(MultiNodeClusterSpec.clusterConfigWithFailureDetectorPuppet))
}

class NodeLeavingAndExitingMultiJvmNode1 extends NodeLeavingAndExitingSpec
class NodeLeavingAndExitingMultiJvmNode2 extends NodeLeavingAndExitingSpec
class NodeLeavingAndExitingMultiJvmNode3 extends NodeLeavingAndExitingSpec

abstract class NodeLeavingAndExitingSpec extends MultiNodeClusterSpec(NodeLeavingAndExitingMultiJvmSpec) {

  import ClusterEvent._
  import NodeLeavingAndExitingMultiJvmSpec._

  "A node that is LEAVING a non-singleton cluster" must {

    "be moved to EXITING by the leader" taggedAs LongRunningTest in {

      awaitClusterUp(first, second, third)

      runOn(first, third) {
        val secondAddess = address(second)
        val exitingLatch = TestLatch()
        cluster.subscribe(
          system.actorOf(Props(new Actor {
            def receive = {
              case state: CurrentClusterState =>
                if (state.members.exists(m => m.address == secondAddess && m.status == Exiting))
                  exitingLatch.countDown()
              case MemberExited(m) if m.address == secondAddess => exitingLatch.countDown()
              case _: MemberRemoved                             => // not tested here
            }
          }).withDeploy(Deploy.local)), classOf[MemberEvent])
        enterBarrier("registered-listener")

        runOn(third) {
          cluster.leave(second)
        }
        enterBarrier("second-left")

        // Verify that 'second' node is set to EXITING
        exitingLatch.await
      }

      // node that is leaving
      runOn(second) {
        enterBarrier("registered-listener")
        enterBarrier("second-left")
      }

      enterBarrier("finished")
    }
  }
}
