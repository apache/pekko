/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import scala.annotation.nowarn
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor._
import pekko.actor.ActorIdentity
import pekko.actor.Identify
import pekko.remote.{ RARP, RemotingMultiNodeSpec }
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.transport.ThrottlerTransportAdapter.Direction
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object SurviveNetworkPartitionSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(
    debugConfig(on = false)
      .withFallback(ConfigFactory.parseString("""
      pekko.loglevel = INFO
      pekko.remote.artery.enabled = on
      pekko.remote.artery.advanced.give-up-system-message-after = 4s
      pekko.remote.use-unsafe-remote-features-outside-cluster = on
      """))
      .withFallback(RemotingMultiNodeSpec.commonConfig))

  testTransport(on = true)
}

class SurviveNetworkPartitionSpecMultiJvmNode1 extends SurviveNetworkPartitionSpec
class SurviveNetworkPartitionSpecMultiJvmNode2 extends SurviveNetworkPartitionSpec

@nowarn("msg=deprecated")
abstract class SurviveNetworkPartitionSpec extends RemotingMultiNodeSpec(SurviveNetworkPartitionSpec) {

  import SurviveNetworkPartitionSpec._

  override def initialParticipants = roles.size

  "Network partition" must {

    "not quarantine system when it heals within 'give-up-system-message-after'" taggedAs LongRunningTest in {

      runOn(second) {
        system.actorOf(TestActors.echoActorProps, "echo1")
      }
      enterBarrier("echo-started")

      runOn(first) {
        system.actorSelection(node(second) / "user" / "echo1") ! Identify(None)
        val ref = expectMsgType[ActorIdentity].ref.get
        ref ! "ping1"
        expectMsg("ping1")

        // network partition
        testConductor.blackhole(first, second, Direction.Both).await

        // send system message during network partition
        watch(ref)
        // keep the network partition for a while, but shorter than give-up-system-message-after
        expectNoMessage(RARP(system).provider.remoteSettings.Artery.Advanced.GiveUpSystemMessageAfter - 2.second)

        // heal the network partition
        testConductor.passThrough(first, second, Direction.Both).await

        // not quarantined
        ref ! "ping2"
        expectMsg("ping2")

        ref ! PoisonPill
        expectTerminated(ref)
      }

      enterBarrier("done")
    }

    "quarantine system when it doesn't heal within 'give-up-system-message-after'" taggedAs LongRunningTest in {

      runOn(second) {
        system.actorOf(TestActors.echoActorProps, "echo2")
      }
      enterBarrier("echo-started")

      runOn(first) {
        val qProbe = TestProbe()
        system.eventStream.subscribe(qProbe.ref, classOf[QuarantinedEvent])
        system.actorSelection(node(second) / "user" / "echo2") ! Identify(None)
        val ref = expectMsgType[ActorIdentity].ref.get
        ref ! "ping1"
        expectMsg("ping1")

        // network partition
        testConductor.blackhole(first, second, Direction.Both).await

        // send system message during network partition
        watch(ref)
        // keep the network partition for a while, longer than give-up-system-message-after
        expectNoMessage(RARP(system).provider.remoteSettings.Artery.Advanced.GiveUpSystemMessageAfter - 1.second)
        qProbe.expectMsgType[QuarantinedEvent](5.seconds).uniqueAddress.address should ===(node(second).address)

        expectTerminated(ref)
      }

      enterBarrier("done")
    }

  }
}
