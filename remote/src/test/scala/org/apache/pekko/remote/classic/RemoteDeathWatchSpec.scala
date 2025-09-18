/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.classic

import scala.annotation.nowarn
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ RootActorPath, _ }
import pekko.event.Logging.Warning
import pekko.remote.{ QuarantinedEvent, RARP, RemoteActorRef }
import pekko.testkit.{ SocketUtil, _ }

import com.typesafe.config.ConfigFactory

@nowarn // classic deprecated
class RemoteDeathWatchSpec
    extends PekkoSpec(ConfigFactory.parseString("""
pekko {
    actor {
        provider = remote
        deployment {
            /watchers.remote = "pekko.tcp://other@localhost:2666"
        }

    }
    remote.use-unsafe-remote-features-outside-cluster = on
    remote.artery.enabled = off
    remote.classic {
      retry-gate-closed-for = 1 s
      initial-system-message-delivery-timeout = 3 s
      netty.tcp {
        hostname = "localhost"
        port = 0
      }
    }
}
# test is using Java serialization and not priority to rewrite
pekko.actor.allow-java-serialization = on
pekko.actor.warn-about-java-serializer-usage = off
"""))
    with ImplicitSender
    with DefaultTimeout
    with DeathWatchSpec {

  val protocol =
    if (RARP(system).provider.remoteSettings.Artery.Enabled) "pekko"
    else "pekko.tcp"

  val other = ActorSystem(
    "other",
    ConfigFactory.parseString("""
        pekko.loglevel = DEBUG
        pekko.remote.artery.enabled = off
        pekko.remote.classic.netty.tcp.port=2666
                              """).withFallback(system.settings.config))

  override def beforeTermination(): Unit = {
    system.eventStream.publish(TestEvent.Mute(EventFilter.warning(pattern = "received dead letter.*Disassociate")))
  }

  override def afterTermination(): Unit = {
    shutdown(other)
  }

  override def expectedTestDuration: FiniteDuration = 120.seconds

  "receive Terminated when system of de-serialized ActorRef is not running" in {
    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[QuarantinedEvent])
    val rarp = RARP(system).provider
    // pick an unused port
    val port = SocketUtil.temporaryLocalPort()
    // simulate de-serialized ActorRef
    val ref = rarp.resolveActorRef(s"$protocol://OtherSystem@localhost:$port/user/foo/bar#1752527294")
    system.actorOf(Props(new Actor {
      context.watch(ref)
      def receive = {
        case Terminated(r) => testActor ! r
      }
    }).withDeploy(Deploy.local))

    expectMsg(20.seconds, ref)
    // we don't expect real quarantine when the UID is unknown, i.e. QuarantinedEvent is not published
    probe.expectNoMessage(3.seconds)
    // The following verifies ticket #3870, i.e. make sure that re-delivery of Watch message is stopped.
    // It was observed as periodic logging of "address is now gated" when the gate was lifted.
    system.eventStream.subscribe(probe.ref, classOf[Warning])
    probe.expectNoMessage(rarp.remoteSettings.RetryGateClosedFor * 2)
  }

  "receive Terminated when watched node is unknown host" in {
    val path = RootActorPath(Address(protocol, system.name, "unknownhost", 7355)) / "user" / "subject"

    system.actorOf(Props(new Actor {
        @nowarn
        val watchee = RARP(context.system).provider.resolveActorRef(path)
        context.watch(watchee)

        def receive = {
          case t: Terminated => testActor ! t.actor.path
        }
      }).withDeploy(Deploy.local), name = "observer2")

    expectMsg(60.seconds, path)
  }

  "receive ActorIdentity(None) when identified node is unknown host" in {
    val path = RootActorPath(Address(protocol, system.name, "unknownhost2", 7355)) / "user" / "subject"
    system.actorSelection(path) ! Identify(path)
    expectMsg(60.seconds, ActorIdentity(path, None))
  }

  "quarantine systems after unsuccessful system message delivery if have not communicated before" in {
    // Synthesize an ActorRef to a remote system this one has never talked to before.
    // This forces ReliableDeliverySupervisor to start with unknown remote system UID.
    val extinctPath = RootActorPath(Address(protocol, "extinct-system", "localhost",
      SocketUtil.temporaryLocalPort())) / "user" / "noone"
    val transport = RARP(system).provider.transport
    val extinctRef = new RemoteActorRef(
      transport,
      transport.localAddressForRemote(extinctPath.address),
      extinctPath,
      Nobody,
      props = None,
      deploy = None,
      acceptProtocolNames = Set("pekko", "akka"))

    val probe = TestProbe()
    probe.watch(extinctRef)
    probe.unwatch(extinctRef)

    probe.expectNoMessage(5.seconds)
    system.eventStream.subscribe(probe.ref, classOf[Warning])
    probe.expectNoMessage(RARP(system).provider.remoteSettings.RetryGateClosedFor * 2)
  }

}
