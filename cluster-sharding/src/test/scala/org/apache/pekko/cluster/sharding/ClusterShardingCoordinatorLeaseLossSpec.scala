/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.cluster.sharding

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.coordination.lease.TestLease
import pekko.coordination.lease.TestLeaseExt
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors
import pekko.testkit.TestException

import com.typesafe.config.ConfigFactory

object ClusterShardingCoordinatorLeaseLossSpec {
  val config = ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.actor.provider = cluster
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.cluster.sharding.coordinator-singleton {
      use-lease = "test-lease"
      lease-retry-interval = 200ms
    }
  """).withFallback(TestLease.config)
}

class ClusterShardingCoordinatorLeaseLossSpec
    extends PekkoSpec(ClusterShardingCoordinatorLeaseLossSpec.config)
    with ImplicitSender {

  private val cluster = Cluster(system)

  override protected def atStartup(): Unit = {
    cluster.join(cluster.selfAddress)
    awaitAssert(cluster.selfMember.status shouldEqual MemberStatus.Up)
  }

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case i: Int => (i.toString, i)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case i: Int => (i % 10).toString
    case _      => "0"
  }

  "Cluster sharding with a coordinator singleton lease" should {

    "keep the local shard region alive when the coordinator lease is lost" in {
      val typeName = "coordinator-lease-loss"
      val region = ClusterSharding(system).start(
        typeName,
        TestActors.echoActorProps,
        ClusterShardingSettings(system),
        extractEntityId,
        extractShardId)

      // the coordinator singleton acquires a lease before starting
      val encName = URLEncoder.encode(typeName, StandardCharsets.UTF_8.name())
      val leaseName = s"${system.name}-singleton-pekko://${system.name}/system/sharding/${encName}Coordinator"
      val coordinatorLease = awaitAssert {
        TestLeaseExt(system).getTestLease(leaseName)
      }
      coordinatorLease.setNextCheckLeaseResult(true)
      // the coordinator singleton acquires the lease before starting
      coordinatorLease.probe.expectMsg(TestLease.AcquireReq(cluster.selfAddress.hostPort))
      coordinatorLease.initialPromise.complete(Success(true))

      // entity is reachable
      region ! 1
      expectMsg(10.seconds, 1)

      // a transient lease backend problem: the singleton manager terminates the
      // coordinator and tries to re-acquire the lease while this node stays Up
      coordinatorLease.getCurrentCallback()(Some(TestException("blip")))

      // wait for the re-acquisition; without the fix the local region has been
      // gracefully shut down by then and can never serve entities again
      val owner = cluster.selfAddress.hostPort
      coordinatorLease.probe.fishForMessage(10.seconds, "lease re-acquired after loss") {
        case TestLease.AcquireReq(`owner`) => true
        case _                             => false
      }

      // the local region must have survived the coordinator restart and keep serving entities
      awaitAssert(
        {
          region ! 2
          expectMsg(5.seconds, 2)
        },
        max = 20.seconds)
    }
  }
}
