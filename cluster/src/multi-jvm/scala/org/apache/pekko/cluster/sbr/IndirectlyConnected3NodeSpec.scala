/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sbr

import scala.concurrent.duration._

import org.apache.pekko
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.MultiNodeClusterSpec
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.transport.ThrottlerTransportAdapter

import com.typesafe.config.ConfigFactory

object IndirectlyConnected3NodeSpec extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  commonConfig(ConfigFactory.parseString("""
    pekko {
      loglevel = INFO
      cluster {
        downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        split-brain-resolver.active-strategy = keep-majority
        split-brain-resolver.stable-after = 6s

        run-coordinated-shutdown-when-down = off
      }

      actor.provider = cluster

      test.filter-leeway = 10s
    }
  """))

  testTransport(on = true)
}

class IndirectlyConnected3NodeSpecMultiJvmNode1 extends IndirectlyConnected3NodeSpec
class IndirectlyConnected3NodeSpecMultiJvmNode2 extends IndirectlyConnected3NodeSpec
class IndirectlyConnected3NodeSpecMultiJvmNode3 extends IndirectlyConnected3NodeSpec

class IndirectlyConnected3NodeSpec extends MultiNodeClusterSpec(IndirectlyConnected3NodeSpec) {
  import IndirectlyConnected3NodeSpec._

  "A 3-node cluster" should {
    "avoid a split brain when two unreachable but can talk via third" in {
      val cluster = Cluster(system)

      runOn(node1) {
        cluster.join(cluster.selfAddress)
      }
      enterBarrier("node1 joined")
      runOn(node2, node3) {
        cluster.join(node(node1).address)
      }
      within(10.seconds) {
        awaitAssert {
          cluster.state.members.size should ===(3)
          cluster.state.members.foreach {
            _.status should ===(MemberStatus.Up)
          }
        }
      }
      enterBarrier("Cluster formed")

      runOn(node1) {
        testConductor.blackhole(node2, node3, ThrottlerTransportAdapter.Direction.Both).await
      }
      enterBarrier("Blackholed")

      within(10.seconds) {
        awaitAssert {
          runOn(node3) {
            cluster.state.unreachable.map(_.address) should ===(Set(node(node2).address))
          }
          runOn(node2) {
            cluster.state.unreachable.map(_.address) should ===(Set(node(node3).address))
          }
          runOn(node1) {
            cluster.state.unreachable.map(_.address) should ===(Set(node(node3).address, node(node2).address))
          }
        }
      }
      enterBarrier("unreachable")

      runOn(node1) {
        within(15.seconds) {
          awaitAssert {
            cluster.state.members.map(_.address) should ===(Set(node(node1).address))
            cluster.state.members.foreach {
              _.status should ===(MemberStatus.Up)
            }
          }
        }
      }

      runOn(node2, node3) {
        // downed
        awaitCond(cluster.isTerminated, max = 15.seconds)
      }

      enterBarrier("done")
    }
  }

}
