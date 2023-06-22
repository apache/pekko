/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko.remote.testkit.MultiNodeConfig

object MultiDcLastNodeSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString(s"""
      pekko.loglevel = INFO
    """).withFallback(MultiNodeClusterSpec.clusterConfig))

  nodeConfig(first, second)(ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "dc1"
    """))

  nodeConfig(third)(ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "dc2"
    """))

}

class MultiDcLastNodeMultiJvmNode1 extends MultiDcLastNodeSpec
class MultiDcLastNodeMultiJvmNode2 extends MultiDcLastNodeSpec
class MultiDcLastNodeMultiJvmNode3 extends MultiDcLastNodeSpec

abstract class MultiDcLastNodeSpec extends MultiNodeClusterSpec(MultiDcLastNodeSpec) {

  import MultiDcLastNodeSpec._

  "A multi-dc cluster with one remaining node in other DC" must {
    "join" in {

      runOn(first) {
        cluster.join(first)
      }
      runOn(second, third) {
        cluster.join(first)
      }
      enterBarrier("join-cluster")

      within(20.seconds) {
        awaitAssert(clusterView.members.filter(_.status == MemberStatus.Up) should have size 3)
      }

      enterBarrier("cluster started")
    }

    "be able to leave" in {
      runOn(third) {
        // this works in same way for down
        cluster.leave(address(third))
      }

      runOn(first, second) {
        awaitAssert(clusterView.members.map(_.address) should not contain address(third))
      }
      enterBarrier("cross-data-center-left")
    }
  }
}
