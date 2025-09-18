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

import org.apache.pekko
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object JoinInProgressMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(
    debugConfig(on = false).withFallback(ConfigFactory.parseString("""
          pekko.cluster {
            # simulate delay in gossip by turning it off
            gossip-interval = 300 s
            failure-detector {
              threshold = 4
              acceptable-heartbeat-pause = 1 second
            }
          }""").withFallback(MultiNodeClusterSpec.clusterConfig)))
}

class JoinInProgressMultiJvmNode1 extends JoinInProgressSpec
class JoinInProgressMultiJvmNode2 extends JoinInProgressSpec

abstract class JoinInProgressSpec extends MultiNodeClusterSpec(JoinInProgressMultiJvmSpec) {

  import JoinInProgressMultiJvmSpec._

  "A cluster node" must {
    "send heartbeats immediately when joining to avoid false failure detection due to delayed gossip" taggedAs LongRunningTest in {

      runOn(first) {
        startClusterNode()
      }

      enterBarrier("first-started")

      runOn(second) {
        cluster.join(first)
      }

      runOn(first) {
        val until = Deadline.now + 5.seconds
        while (!until.isOverdue()) {
          Thread.sleep(200)
          cluster.failureDetector.isAvailable(second) should ===(true)
        }
      }

      enterBarrier("after")
    }
  }
}
