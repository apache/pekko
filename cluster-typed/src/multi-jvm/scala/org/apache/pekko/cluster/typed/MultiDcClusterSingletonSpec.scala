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

package org.apache.pekko.cluster.typed

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.scaladsl.adapter._
import pekko.cluster.{ MemberStatus, MultiNodeClusterSpec }
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }

object MultiDcClusterSingletonSpecConfig extends MultiNodeConfig {
  val first: RoleName = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString("""
        pekko.loglevel = DEBUG
      """).withFallback(MultiNodeClusterSpec.clusterConfig))

  nodeConfig(first)(ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "dc1"
    """))

  nodeConfig(second, third)(ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "dc2"
    """))

  testTransport(on = true)
}

class MultiDcClusterSingletonMultiJvmNode1 extends MultiDcClusterSingletonSpec
class MultiDcClusterSingletonMultiJvmNode2 extends MultiDcClusterSingletonSpec
class MultiDcClusterSingletonMultiJvmNode3 extends MultiDcClusterSingletonSpec

abstract class MultiDcClusterSingletonSpec
    extends MultiNodeSpec(MultiDcClusterSingletonSpecConfig)
    with MultiNodeTypedClusterSpec {

  import MultiDcClusterSingletonSpecConfig._
  import MultiDcPinger._

  "A cluster with multiple data centers" must {
    "be able to form" in {
      runOn(first) {
        cluster.manager ! Join(cluster.selfMember.address)
      }
      runOn(second, third) {
        cluster.manager ! Join(first)
      }
      enterBarrier("form-cluster-join-attempt")
      runOn(first, second, third) {
        within(20.seconds) {
          awaitAssert(clusterView.members.filter(_.status == MemberStatus.Up) should have size 3)
        }
      }
      enterBarrier("cluster started")
    }

    "be able to create and ping singleton in same DC" in {
      runOn(first) {
        val singleton = ClusterSingleton(typedSystem)
        val pinger = singleton.init(SingletonActor(MultiDcPinger(), "ping").withStopMessage(NoMore))
        val probe = TestProbe[Pong]()
        pinger ! Ping(probe.ref)
        probe.expectMessage(Pong("dc1"))
        enterBarrier("singleton-up")
      }
      runOn(second, third) {
        enterBarrier("singleton-up")
      }
    }

    "be able to ping singleton via proxy in another dc" in {
      runOn(second) {
        val singleton = ClusterSingleton(system.toTyped)
        val pinger = singleton.init(
          SingletonActor(MultiDcPinger(), "ping")
            .withStopMessage(NoMore)
            .withSettings(ClusterSingletonSettings(typedSystem).withDataCenter("dc1")))
        val probe = TestProbe[Pong]()
        pinger ! Ping(probe.ref)
        probe.expectMessage(Pong("dc1"))
      }

      enterBarrier("singleton-pinged")
    }

    "be able to target singleton with the same name in own dc " in {
      runOn(second, third) {
        val singleton = ClusterSingleton(typedSystem)
        val pinger = singleton.init(SingletonActor(MultiDcPinger(), "ping").withStopMessage(NoMore))
        val probe = TestProbe[Pong]()
        pinger ! Ping(probe.ref)
        probe.expectMessage(Pong("dc2"))
      }

      enterBarrier("singleton-pinged-own-dc")
    }
  }
}
