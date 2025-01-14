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

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import language.postfixOps

import org.apache.pekko
import pekko.cluster.ClusterEvent.CurrentClusterState
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.transport.ThrottlerTransportAdapter.Direction
import pekko.testkit._

object InitialHeartbeatMultiJvmSpec extends MultiNodeConfig {
  val controller = role("controller")
  val first = role("first")
  val second = role("second")

  commonConfig(
    debugConfig(on = false).withFallback(ConfigFactory.parseString("""
      pekko.cluster.failure-detector.threshold = 4""")).withFallback(MultiNodeClusterSpec.clusterConfig))

  testTransport(on = true)
}

class InitialHeartbeatMultiJvmNode1 extends InitialHeartbeatSpec
class InitialHeartbeatMultiJvmNode2 extends InitialHeartbeatSpec
class InitialHeartbeatMultiJvmNode3 extends InitialHeartbeatSpec

abstract class InitialHeartbeatSpec extends MultiNodeClusterSpec(InitialHeartbeatMultiJvmSpec) {

  import InitialHeartbeatMultiJvmSpec._

  muteMarkingAsUnreachable()

  "A member" must {

    "detect failure even though no heartbeats have been received" taggedAs LongRunningTest in {
      val firstAddress = address(first)
      val secondAddress = address(second)
      awaitClusterUp(first)

      runOn(first) {
        within(10.seconds) {
          awaitAssert({
              cluster.sendCurrentClusterState(testActor)
              expectMsgType[CurrentClusterState].members.map(_.address) should contain(secondAddress)
            }, interval = 50.millis)
        }
      }
      runOn(second) {
        cluster.join(first)
        within(10.seconds) {
          awaitAssert({
              cluster.sendCurrentClusterState(testActor)
              expectMsgType[CurrentClusterState].members.map(_.address) should contain(firstAddress)
            }, interval = 50.millis)
        }
      }
      enterBarrier("second-joined")

      runOn(controller) {
        // It is likely that second has not started heartbeating to first yet,
        // and when it does the messages doesn't go through and the first extra heartbeat is triggered.
        // If the first heartbeat arrives, it will detect the failure anyway but not really exercise the
        // part that we are trying to test here.
        testConductor.blackhole(first, second, Direction.Both).await
      }

      runOn(second) {
        within(15.seconds) {
          awaitCond(!cluster.failureDetector.isAvailable(first))
        }
      }

      enterBarrier("after-1")
    }
  }
}
