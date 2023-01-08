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

package org.apache.pekko.cluster.pubsub

import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import language.postfixOps

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Identify
import pekko.actor.Props
import pekko.actor.RootActorPath
import pekko.cluster.Cluster
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.remote.testkit.STMultiNodeSpec
import pekko.testkit._

object DistributedPubSubRestartSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.cluster.pub-sub.gossip-interval = 500ms
    pekko.actor.provider = cluster
    pekko.remote.log-remote-lifecycle-events = off
    pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
    pekko.cluster.testkit.auto-down-unreachable-after = off
    """))

  testTransport(on = true)

  class Shutdown extends Actor {
    def receive = {
      case "shutdown" => context.system.terminate()
    }
  }

}

class DistributedPubSubRestartMultiJvmNode1 extends DistributedPubSubRestartSpec
class DistributedPubSubRestartMultiJvmNode2 extends DistributedPubSubRestartSpec
class DistributedPubSubRestartMultiJvmNode3 extends DistributedPubSubRestartSpec

class DistributedPubSubRestartSpec
    extends MultiNodeSpec(DistributedPubSubRestartSpec)
    with STMultiNodeSpec
    with ImplicitSender {
  import DistributedPubSubMediator._
  import DistributedPubSubRestartSpec._

  override def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system).join(node(to).address)
      createMediator()
    }
    enterBarrier(from.name + "-joined")
  }

  def createMediator(): ActorRef = DistributedPubSub(system).mediator
  def mediator: ActorRef = DistributedPubSub(system).mediator

  def awaitCount(expected: Int): Unit = {
    val probe = TestProbe()
    awaitAssert {
      mediator.tell(Count, probe.ref)
      val actual = probe.expectMsgType[Int]
      actual should ===(expected)
    }
  }

  "A Cluster with DistributedPubSub" must {

    "startup 3 node cluster" in within(15 seconds) {
      join(first, first)
      join(second, first)
      join(third, first)
      enterBarrier("after-1")
    }

    "handle restart of nodes with same address" in within(30 seconds) {
      mediator ! Subscribe("topic1", testActor)
      expectMsgType[SubscribeAck]
      awaitCount(3)

      runOn(first) {
        mediator ! Publish("topic1", "msg1")
      }
      enterBarrier("pub-msg1")

      expectMsg("msg1")
      enterBarrier("got-msg1")

      mediator ! Internal.DeltaCount
      val oldDeltaCount = expectMsgType[Long]
      enterBarrier("old-delta-count")

      runOn(second) {
        enterBarrier("end")

        mediator ! Internal.DeltaCount
        val deltaCount = expectMsgType[Long]
        deltaCount should ===(oldDeltaCount)
      }

      runOn(first) {
        val thirdAddress = node(third).address
        testConductor.shutdown(third).await

        within(20.seconds) {
          awaitAssert {
            val p = TestProbe()
            system.actorSelection(RootActorPath(thirdAddress) / "user" / "shutdown").tell(Identify(None), p.ref)
            p.expectMsgType[ActorIdentity](1.second).ref.get
          }
        }

        system.actorSelection(RootActorPath(thirdAddress) / "user" / "shutdown") ! "shutdown"

        enterBarrier("end")

        mediator ! Internal.DeltaCount
        val deltaCount = expectMsgType[Long]
        deltaCount should ===(oldDeltaCount)
      }

      runOn(third) {
        Await.result(system.whenTerminated, 10.seconds)
        val newSystem = {
          val port = Cluster(system).selfAddress.port.get
          val config = ConfigFactory.parseString(s"""
              pekko.remote.artery.canonical.port=$port
              pekko.remote.classic.netty.tcp.port=$port
              """).withFallback(system.settings.config)

          ActorSystem(system.name, config)
        }

        try {
          // don't join the old cluster
          Cluster(newSystem).join(Cluster(newSystem).selfAddress)
          val newMediator = DistributedPubSub(newSystem).mediator
          val probe = TestProbe()(newSystem)
          newMediator.tell(Subscribe("topic2", probe.ref), probe.ref)
          probe.expectMsgType[SubscribeAck]

          // let them gossip, but Delta should not be exchanged
          probe.expectNoMessage(5.seconds)
          newMediator.tell(Internal.DeltaCount, probe.ref)
          probe.expectMsg(0L)

          newSystem.actorOf(Props[Shutdown](), "shutdown")
          Await.ready(newSystem.whenTerminated, 20.seconds)
        } finally newSystem.terminate()
      }

    }
  }

}
