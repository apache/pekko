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

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.singleton.ClusterSingletonManager.Internal.HandOverDone
import pekko.cluster.singleton.ClusterSingletonManager.Internal.HandOverInProgress
import pekko.cluster.singleton.ClusterSingletonManager.Internal.HandOverToMe
import pekko.coordination.lease.TestLease
import pekko.coordination.lease.TestLeaseExt
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors
import pekko.testkit.TestException
import pekko.testkit.TestProbe

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

  private def startRegion(
      typeName: String,
      entityProps: Props = TestActors.echoActorProps,
      handOffStopMessage: Any = PoisonPill): (ActorRef, TestLease) = {
    val settings = ClusterShardingSettings(system)
    val region = ClusterSharding(system).start(
      typeName,
      entityProps,
      settings,
      extractEntityId,
      extractShardId,
      ShardCoordinator.ShardAllocationStrategy.leastShardAllocationStrategy(1, 1.0),
      handOffStopMessage)
    val encName = URLEncoder.encode(typeName, StandardCharsets.UTF_8.name())
    val leaseName = s"${system.name}-singleton-pekko://${system.name}/system/sharding/${encName}Coordinator"
    val coordinatorLease = awaitAssert {
      TestLeaseExt(system).getTestLease(leaseName)
    }
    coordinatorLease.probe.expectMsg(TestLease.AcquireReq(cluster.selfAddress.hostPort))
    coordinatorLease.initialPromise.complete(Success(true))
    region ! 1
    expectMsg(10.seconds, 1)
    (region, coordinatorLease)
  }

  "Cluster sharding with a coordinator singleton lease" should {

    "keep the local shard region alive when the coordinator lease is lost" in {
      val typeName = "coordinator-lease-loss"
      val (region, coordinatorLease) = startRegion(typeName)
      val singletonPath = region.path.parent / s"${typeName}Coordinator" / "singleton"
      val singleton = system.actorSelection(singletonPath).resolveOne(remainingOrDefault).futureValue
      val terminationProbe = TestProbe()
      terminationProbe.watch(singleton)
      terminationProbe.watch(region)
      val nextAcquire = Promise[Boolean]()
      coordinatorLease.setNextAcquireResult(nextAcquire.future)
      coordinatorLease.getCurrentCallback()(Some(TestException("blip")))

      val owner = cluster.selfAddress.hostPort
      coordinatorLease.probe.fishForMessage(10.seconds, "lease re-acquired after loss") {
        case TestLease.AcquireReq(`owner`) => true
        case _                             => false
      }
      terminationProbe.expectTerminated(singleton)

      // Existing entities remain available even while the coordinator cannot reacquire its lease.
      region ! 1
      expectMsg(1)
      terminationProbe.expectNoMessage(100.millis)
      nextAcquire.success(true)
      region ! 2
      expectMsg(20.seconds, 2)
      val restartedSingleton = system.actorSelection(singletonPath).resolveOne(remainingOrDefault).futureValue
      (restartedSingleton should not).equal(singleton)
    }

    "drain the local region on hand-over before the membership read view changes" in {
      val stopProbe = TestProbe()
      val entityProps = Props(new Actor {
        def receive: Receive = {
          case "stop" => stopProbe.ref ! self
          case msg    => sender() ! msg
        }
      })
      val typeName = "coordinator-hand-over"
      val (region, _) = startRegion(typeName, entityProps, "stop")
      val managerPath = region.path.parent / s"${typeName}Coordinator"
      val manager = system.actorSelection(managerPath).resolveOne(remainingOrDefault).futureValue
      val singleton = system.actorSelection(managerPath / "singleton").resolveOne(remainingOrDefault).futureValue
      val terminationProbe = TestProbe()
      terminationProbe.watch(singleton)
      watch(region)

      // The singleton manager can observe hand-over before the independent cluster read view.
      cluster.selfMember.status shouldBe MemberStatus.Up
      val handOverProbe = TestProbe()
      handOverProbe.send(manager, HandOverToMe)
      handOverProbe.expectMsg(HandOverInProgress)
      val entity = stopProbe.expectMsgType[ActorRef]
      terminationProbe.expectNoMessage(100.millis)
      system.stop(entity)
      expectTerminated(region)
      terminationProbe.expectTerminated(singleton)
      handOverProbe.expectMsg(HandOverDone)
    }
  }
}
