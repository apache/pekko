/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.AddressFromURIString
import pekko.actor.Props
import pekko.actor.RepointableActorRef
import pekko.cluster.ClusterRemoteFeatures.AddressPing
import pekko.remote.RARP
import pekko.remote.RemoteActorRef
import pekko.remote.RemoteActorRefProvider
import pekko.remote.RemoteWatcher.Heartbeat
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.testkit.ImplicitSender

class ClusterRemoteFeaturesConfig(artery: Boolean) extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  private val baseConfig = {
    ConfigFactory.parseString(s"""
      pekko.remote.log-remote-lifecycle-events = off
      pekko.remote.artery.enabled = $artery
      pekko.remote.artery.canonical.port = ${MultiNodeSpec.selfPort}
      pekko.log-dead-letters-during-shutdown = off
      """).withFallback(MultiNodeClusterSpec.clusterConfig)
  }

  commonConfig(debugConfig(on = false).withFallback(baseConfig))

  deployOn(first, """/kattdjur.remote = "@second@" """)
  deployOn(third, """/kattdjur.remote = "@second@" """)
  deployOn(second, """/kattdjur.remote = "@third@" """)

}

object ClusterRemoteFeatures {
  class AddressPing extends Actor {
    def receive: Receive = {
      case "ping" => sender() ! self
    }
  }
}

class ArteryClusterRemoteFeaturesMultiJvmNode1 extends ClusterRemoteFeaturesSpec(new ClusterRemoteFeaturesConfig(true))
class ArteryClusterRemoteFeaturesMultiJvmNode2 extends ClusterRemoteFeaturesSpec(new ClusterRemoteFeaturesConfig(true))
class ArteryClusterRemoteFeaturesMultiJvmNode3 extends ClusterRemoteFeaturesSpec(new ClusterRemoteFeaturesConfig(true))

class ClassicClusterRemoteFeaturesMultiJvmNode1
    extends ClusterRemoteFeaturesSpec(new ClusterRemoteFeaturesConfig(false))
class ClassicClusterRemoteFeaturesMultiJvmNode2
    extends ClusterRemoteFeaturesSpec(new ClusterRemoteFeaturesConfig(false))
class ClassicClusterRemoteFeaturesMultiJvmNode3
    extends ClusterRemoteFeaturesSpec(new ClusterRemoteFeaturesConfig(false))

abstract class ClusterRemoteFeaturesSpec(multiNodeConfig: ClusterRemoteFeaturesConfig)
    extends MultiNodeClusterSpec(multiNodeConfig)
    with ImplicitSender
    with ScalaFutures {

  import multiNodeConfig._

  override def initialParticipants: Int = roles.size

  muteDeadLetters(Heartbeat.getClass)()

  protected val provider: RemoteActorRefProvider = RARP(system).provider

  "Remoting with Cluster" must {

    "have the correct settings" in {
      runOn(first) {
        system.settings.HasCluster shouldBe true
        provider.hasClusterOrUseUnsafe shouldBe true
        provider.transport.system.settings.HasCluster shouldBe true
        provider.remoteSettings.UseUnsafeRemoteFeaturesWithoutCluster shouldBe false
        system.settings.ProviderClass shouldEqual classOf[ClusterActorRefProvider].getName
      }
    }

    "create a ClusterRemoteWatcher" in {
      runOn(roles: _*)(provider.remoteWatcher.isDefined shouldBe true)
    }

    "create a ClusterDeployer" in {
      runOn(roles: _*)(provider.deployer.getClass shouldEqual classOf[ClusterDeployer])
    }

    "have expected `actorOf` behavior" in {
      awaitClusterUp(first, second)
      enterBarrier("cluster-up")

      runOn(first) {
        val actor = system.actorOf(Props[AddressPing](), "kattdjur")
        actor.isInstanceOf[RemoteActorRef] shouldBe true
        actor.path.address shouldEqual node(second).address
        actor.path.address.hasGlobalScope shouldBe true

        val secondAddress = node(second).address
        actor ! "ping"
        expectMsgType[RemoteActorRef].path.address shouldEqual secondAddress
      }
      enterBarrier("CARP-in-cluster-remote-validated")

      def assertIsLocalRef(): Unit = {
        val actor = system.actorOf(Props[AddressPing](), "kattdjur")
        actor.isInstanceOf[RepointableActorRef] shouldBe true
        val localAddress = AddressFromURIString(s"akka://${system.name}")
        actor.path.address shouldEqual localAddress
        actor.path.address.hasLocalScope shouldBe true

        actor ! "ping"
        expectMsgType[ActorRef].path.address shouldEqual localAddress
      }

      runOn(third) {
        Cluster(system).state.isMemberUp(node(third).address) shouldBe false
        assertIsLocalRef()
      }
      enterBarrier("CARP-outside-cluster-local-validated")

      runOn(second) {
        assertIsLocalRef()
      }
      enterBarrier("CARP-inside-cluster-to-non-member-local-validated")
    }
  }
}
