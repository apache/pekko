/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote

import scala.concurrent.duration._

import scala.annotation.nowarn
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.AddressFromURIString
import pekko.actor.Identify
import pekko.actor.InternalActorRef
import pekko.actor.Props
import pekko.actor.RootActorPath
import pekko.remote.RemoteWatcher.Stats
import pekko.remote.RemoteWatcher.UnwatchRemote
import pekko.remote.RemoteWatcher.WatchRemote
import pekko.remote.artery.ArteryMultiNodeSpec
import pekko.remote.artery.ArterySpecSupport
import pekko.remote.artery.RemoteDeploymentSpec
import pekko.testkit.EventFilter
import pekko.testkit.ImplicitSender
import pekko.testkit.TestProbe

object RemoteFeaturesSpec {

  val instances = 1

  // string config to pass into `ArteryMultiNodeSpec.extraConfig: Option[String]` for `other` system
  def common(useUnsafe: Boolean): String = s"""
       pekko.remote.use-unsafe-remote-features-outside-cluster = $useUnsafe
       pekko.remote.artery.enabled = on
       pekko.remote.artery.canonical.port = 0
       pekko.log-dead-letters-during-shutdown = off
       """

  def disabled: Config =
    ConfigFactory.parseString(common(useUnsafe = false)).withFallback(ArterySpecSupport.defaultConfig)
  def enabled: Config = ConfigFactory.parseString(common(useUnsafe = true))

  class EmptyActor extends Actor {
    def receive: Receive = Actor.emptyBehavior
  }
}

abstract class RemoteFeaturesSpec(c: Config) extends ArteryMultiNodeSpec(c) with ImplicitSender {
  import RemoteFeaturesSpec._

  protected final val provider = RARP(system).provider

  protected final val useUnsafe: Boolean = provider.remoteSettings.UseUnsafeRemoteFeaturesWithoutCluster

  protected val remoteSystem1 = newRemoteSystem(name = Some("RS1"), extraConfig = Some(common(useUnsafe)))

  @nowarn("msg=deprecated")
  private def mute(): Unit =
    Seq(system, remoteSystem1).foreach(
      muteDeadLetters(
        pekko.remote.transport.AssociationHandle.Disassociated.getClass,
        pekko.remote.transport.ActorTransportAdapter.DisassociateUnderlying.getClass)(_))
  mute()

  import pekko.remote.artery.RemoteWatcherSpec.TestRemoteWatcher
  protected val monitor = system.actorOf(Props(new TestRemoteWatcher), "monitor1")

  protected val watcher = system.actorOf(Props(new EmptyActor), "a1").asInstanceOf[InternalActorRef]

  protected val remoteWatchee = createRemoteActor(Props(new EmptyActor), "b1")

  protected def createRemoteActor(props: Props, name: String): InternalActorRef = {
    remoteSystem1.actorOf(props, name)
    system.actorSelection(RootActorPath(address(remoteSystem1)) / "user" / name) ! Identify(name)
    expectMsgType[ActorIdentity].ref.get.asInstanceOf[InternalActorRef]
  }
}

// all pre-existing remote tests exercise the rest of the unchanged enabled expectations
class RARPRemoteFeaturesEnabledSpec extends RemoteFeaturesSpec(RemoteFeaturesSpec.enabled) {
  "RARP without Cluster: opt-in unsafe enabled" must {

    "have the expected settings" in {
      provider.transport.system.settings.HasCluster shouldBe false
      provider.remoteSettings.UseUnsafeRemoteFeaturesWithoutCluster shouldBe true
      provider.remoteSettings.WarnUnsafeWatchWithoutCluster shouldBe true
      provider.hasClusterOrUseUnsafe shouldBe true
    }

    "create a RemoteWatcher" in {
      provider.remoteWatcher.isDefined shouldBe true
    }
  }
}

// see the multi-jvm RemoteFeaturesSpec for deployer-router tests
class RemoteFeaturesDisabledSpec extends RemoteFeaturesSpec(RemoteFeaturesSpec.disabled) {

  private val actorName = "kattdjur"

  private val port = RARP(system).provider.getDefaultAddress.port.get

  "Remote features without Cluster" must {

    "have the expected settings in a RARP" in {
      provider.transport.system.settings.HasCluster shouldBe false
      provider.remoteSettings.UseUnsafeRemoteFeaturesWithoutCluster shouldBe false
      provider.remoteSettings.WarnUnsafeWatchWithoutCluster shouldBe true
      provider.hasClusterOrUseUnsafe shouldBe false
    }

    "not create a RemoteWatcher in a RARP" in {
      provider.remoteWatcher shouldEqual None
    }

    "not deathwatch a remote actor" in {
      EventFilter
        .warning(pattern = s"Dropped remote Watch: disabled for *", occurrences = 1)
        .intercept(monitor ! WatchRemote(remoteWatchee, watcher))
      monitor ! Stats
      expectMsg(Stats.empty)
      expectNoMessage(100.millis)

      EventFilter
        .warning(pattern = s"Dropped remote Unwatch: disabled for *", occurrences = 1)
        .intercept(monitor ! UnwatchRemote(remoteWatchee, watcher))

      monitor ! Stats
      expectMsg(Stats.empty)
      expectNoMessage(100.millis)
    }

    "fall back to creating local deploy children and supervise children on local node" in {
      // super.newRemoteSystem adds the new system to shutdown hook
      val masterSystem = newRemoteSystem(
        name = Some("RS2"),
        extraConfig = Some(s"""
      pekko.actor.deployment {
        /$actorName.remote = "pekko://${system.name}@localhost:$port"
        "/parent*/*".remote = "pekko://${system.name}@localhost:$port"
      }
    """))

      val masterRef = masterSystem.actorOf(Props[RemoteDeploymentSpec.Echo1](), actorName)
      masterRef.path shouldEqual RootActorPath(
        AddressFromURIString(s"pekko://${masterSystem.name}")) / "user" / actorName
      masterRef.path.address.hasLocalScope shouldBe true

      masterSystem.actorSelection(RootActorPath(address(system)) / "user" / actorName) ! Identify(1)
      expectMsgType[ActorIdentity].ref shouldEqual None

      system.actorSelection(RootActorPath(address(system)) / "user" / actorName) ! Identify(3)
      expectMsgType[ActorIdentity].ref
        .forall(_.path == RootActorPath(address(masterSystem)) / "user" / actorName) shouldBe true

      val senderProbe = TestProbe()(masterSystem)
      masterRef.tell(42, senderProbe.ref)
      senderProbe.expectMsg(42)
      EventFilter[Exception]("crash", occurrences = 1).intercept {
        masterRef ! "throwException"
      }(masterSystem)
      senderProbe.expectMsg("preRestart")
      masterRef.tell(43, senderProbe.ref)
      senderProbe.expectMsg(43)
      masterSystem.stop(masterRef)
      senderProbe.expectMsg("postStop")
    }
  }
}
