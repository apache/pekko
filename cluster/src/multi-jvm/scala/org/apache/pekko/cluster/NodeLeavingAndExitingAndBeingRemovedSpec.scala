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

import org.apache.pekko
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

object NodeLeavingAndExitingAndBeingRemovedMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(debugConfig(on = false).withFallback(MultiNodeClusterSpec.clusterConfigWithFailureDetectorPuppet))
}

class NodeLeavingAndExitingAndBeingRemovedMultiJvmNode1 extends NodeLeavingAndExitingAndBeingRemovedSpec
class NodeLeavingAndExitingAndBeingRemovedMultiJvmNode2 extends NodeLeavingAndExitingAndBeingRemovedSpec
class NodeLeavingAndExitingAndBeingRemovedMultiJvmNode3 extends NodeLeavingAndExitingAndBeingRemovedSpec

abstract class NodeLeavingAndExitingAndBeingRemovedSpec
    extends MultiNodeClusterSpec(NodeLeavingAndExitingAndBeingRemovedMultiJvmSpec) {

  import NodeLeavingAndExitingAndBeingRemovedMultiJvmSpec._

  "A node that is LEAVING a non-singleton cluster" must {

    "eventually set to REMOVED and removed from membership ring and seen table" taggedAs LongRunningTest in {

      awaitClusterUp(first, second, third)

      within(15.seconds) {
        runOn(first) {
          cluster.leave(second)
        }
        enterBarrier("second-left")

        runOn(first, third) {
          enterBarrier("second-shutdown")
          // this test verifies that the removal is performed via the ExitingCompleted message,
          // otherwise we would have `markNodeAsUnavailable(second)` to trigger the FailureDetectorPuppet

          // verify that the 'second' node is no longer part of the 'members'/'unreachable' set
          awaitAssert {
            clusterView.members.map(_.address) should not contain (address(second))
          }
          awaitAssert {
            clusterView.unreachableMembers.map(_.address) should not contain (address(second))
          }
        }

        runOn(second) {
          // verify that the second node is shut down
          awaitCond(cluster.isTerminated)
          enterBarrier("second-shutdown")
        }
      }

      enterBarrier("finished")
    }
  }
}
