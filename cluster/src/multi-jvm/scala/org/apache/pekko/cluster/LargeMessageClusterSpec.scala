/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import java.io.NotSerializableException
import java.nio.charset.StandardCharsets

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.ExtendedActorSystem
import pekko.actor.Identify
import pekko.cluster.ClusterEvent.UnreachableMember
import pekko.remote.RARP
import pekko.remote.artery.ArterySettings
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.serialization.SerializerWithStringManifest
import pekko.testkit._
import pekko.util.unused

object LargeMessageClusterMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  // Note that this test uses default configuration,
  // not MultiNodeClusterSpec.clusterConfig
  commonConfig(ConfigFactory.parseString(s"""
    pekko {
      cluster.debug.verbose-heartbeat-logging = on
      loggers = ["org.apache.pekko.testkit.TestEventListener"]

      actor {
        provider = cluster

        serializers {
          test = "org.apache.pekko.cluster.LargeMessageClusterMultiJvmSpec$$SlowSerializer"
        }
        serialization-bindings {
          "org.apache.pekko.cluster.LargeMessageClusterMultiJvmSpec$$Slow" = test
        }
      }

      testconductor.barrier-timeout = 3 minutes

      cluster.failure-detector.acceptable-heartbeat-pause = 5 s

      remote.artery {
        enabled = on

        large-message-destinations = [ "/user/largeEcho", "/system/largeEchoProbe-3" ]

        advanced {
          maximum-large-frame-size = 2 MiB
          large-buffer-pool-size = 32
        }
      }
    }
    """))

  final case class Slow(payload: Array[Byte])

  class SlowSerializer(@unused system: ExtendedActorSystem) extends SerializerWithStringManifest {
    override def identifier = 999
    override def manifest(o: AnyRef) = "a"
    override def toBinary(o: AnyRef) = o match {
      case Slow(payload) =>
        // simulate slow serialization to not completely overload the machine/network, see issue #24576
        Thread.sleep(100)
        payload
      case _ => throw new NotSerializableException()
    }
    override def fromBinary(bytes: Array[Byte], manifest: String) = {
      Slow(bytes)
    }
  }

}

class LargeMessageClusterMultiJvmNode1 extends LargeMessageClusterSpec
class LargeMessageClusterMultiJvmNode2 extends LargeMessageClusterSpec
class LargeMessageClusterMultiJvmNode3 extends LargeMessageClusterSpec

abstract class LargeMessageClusterSpec
    extends MultiNodeClusterSpec(LargeMessageClusterMultiJvmSpec)
    with ImplicitSender {
  import LargeMessageClusterMultiJvmSpec._

  override def expectedTestDuration: FiniteDuration = 3.minutes

  def identify(role: RoleName, actorName: String): ActorRef = within(10.seconds) {
    system.actorSelection(node(role) / "user" / actorName) ! Identify(actorName)
    expectMsgType[ActorIdentity].ref.get
  }

  val unreachableProbe = TestProbe()

  "Artery Cluster with large messages" must {

    if (!RARP(system).provider.remoteSettings.Artery.Enabled) {
      info(s"${getClass.getName} is only enabled for Artery")
      pending
    }

    "init cluster" taggedAs LongRunningTest in {
      Cluster(system).subscribe(unreachableProbe.ref, ClusterEvent.InitialStateAsEvents, classOf[UnreachableMember])

      awaitClusterUp(first, second, third)

      // let heartbeat monitoring begin
      unreachableProbe.expectNoMessage(5.seconds)

      enterBarrier("init-done")
    }

    "not disturb cluster heartbeat messages when sent in bursts" taggedAs LongRunningTest in {

      runOn(second, third) {
        system.actorOf(TestActors.echoActorProps, "echo")
        system.actorOf(TestActors.echoActorProps, "largeEcho")
      }
      enterBarrier("actors-started")

      runOn(second) {
        val echo3 = identify(third, "echo")
        val largeEcho3 = identify(third, "largeEcho")
        val largeEchoProbe = TestProbe(name = "largeEchoProbe")

        val largeMsgSize = 2 * 1000 * 1000
        val largeMsg = ("0" * largeMsgSize).getBytes(StandardCharsets.UTF_8)
        val largeMsgBurst = 3
        val repeat = 15
        for (n <- 1 to repeat) {
          val startTime = System.nanoTime()
          for (_ <- 1 to largeMsgBurst) {
            largeEcho3.tell(largeMsg, largeEchoProbe.ref)
          }

          val ordinaryProbe = TestProbe()
          echo3.tell(("0" * 1000).getBytes(StandardCharsets.UTF_8), ordinaryProbe.ref)
          ordinaryProbe.expectMsgType[Array[Byte]]
          val ordinaryDurationMs = (System.nanoTime() - startTime) / 1000 / 1000

          largeEchoProbe.receiveN(largeMsgBurst, 20.seconds)
          println(s"Burst $n took ${(System.nanoTime() - startTime) / 1000 / 1000} ms, ordinary $ordinaryDurationMs ms")
        }
      }
      enterBarrier("sending-complete-1")

      unreachableProbe.expectNoMessage(1.seconds)

      enterBarrier("after-1")
    }

    "not disturb cluster heartbeat messages when saturated" taggedAs LongRunningTest in {

      // for non Aeron transport we use the Slow message and SlowSerializer to slow down
      // to not completely overload the machine/network, see issue #24576
      val arterySettings = ArterySettings(system.settings.config.getConfig("pekko.remote.artery"))
      val aeronUdpEnabled = arterySettings.Enabled && arterySettings.Transport == ArterySettings.AeronUpd

      runOn(second) {
        val largeEcho2 = identify(second, "largeEcho")
        val largeEcho3 = identify(third, "largeEcho")

        val largeMsgSize = 1 * 1000 * 1000
        val payload = ("0" * largeMsgSize).getBytes(StandardCharsets.UTF_8)
        val largeMsg = if (aeronUdpEnabled) payload else Slow(payload)
        (1 to 3).foreach { _ =>
          // this will ping-pong between second and third
          largeEcho2.tell(largeMsg, largeEcho3)
        }
      }

      unreachableProbe.expectNoMessage(10.seconds)

      enterBarrier("after-2")
    }
  }
}
