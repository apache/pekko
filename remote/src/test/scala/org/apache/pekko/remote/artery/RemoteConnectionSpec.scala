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

package org.apache.pekko.remote.artery

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.testkit.{ EventFilter, ImplicitSender, TestActors, TestEvent, TestProbe }

class RemoteConnectionSpec extends ArteryMultiNodeSpec("pekko.remote.retry-gate-closed-for = 5s") with ImplicitSender {

  def muteSystem(system: ActorSystem): Unit = {
    system.eventStream.publish(
      TestEvent.Mute(
        EventFilter.error(start = "AssociationError"),
        EventFilter.warning(start = "AssociationError"),
        EventFilter.warning(pattern = "received dead letter.*")))
  }

  "Remoting between systems" should {

    "be able to connect to system even if it's not there at first" in {
      muteSystem(localSystem)
      val localProbe = new TestProbe(localSystem)

      val remotePort = freePort()

      // try to talk to it before it is up
      val selection = localSystem.actorSelection(s"pekko://$nextGeneratedSystemName@localhost:$remotePort/user/echo")
      selection.tell("ping", localProbe.ref)
      localProbe.expectNoMessage(1.seconds)

      // then start the remote system and try again
      val remoteSystem = newRemoteSystem(extraConfig = Some(s"pekko.remote.artery.canonical.port=$remotePort"))

      muteSystem(remoteSystem)
      localProbe.expectNoMessage(2.seconds)
      remoteSystem.actorOf(TestActors.echoActorProps, "echo")

      within(5.seconds) {
        awaitAssert {
          selection.tell("ping", localProbe.ref)
          localProbe.expectMsg(500.millis, "ping")
        }
      }
    }

    "allow other system to connect even if it's not there at first" in {
      val localSystem = newRemoteSystem()

      val localPort = port(localSystem)
      muteSystem(localSystem)

      val localProbe = new TestProbe(localSystem)
      localSystem.actorOf(TestActors.echoActorProps, "echo")

      val remotePort = freePort()

      // try to talk to remote before it is up
      val selection = localSystem.actorSelection(s"pekko://$nextGeneratedSystemName@localhost:$remotePort/user/echo")
      selection.tell("ping", localProbe.ref)
      localProbe.expectNoMessage(1.seconds)

      // then when it is up, talk from other system
      val remoteSystem = newRemoteSystem(extraConfig = Some(s"pekko.remote.artery.canonical.port=$remotePort"))

      muteSystem(remoteSystem)
      localProbe.expectNoMessage(2.seconds)
      val otherProbe = new TestProbe(remoteSystem)
      val otherSender = otherProbe.ref
      val thisSelection = remoteSystem.actorSelection(s"pekko://${localSystem.name}@localhost:$localPort/user/echo")
      within(5.seconds) {
        awaitAssert {
          thisSelection.tell("ping", otherSender)
          otherProbe.expectMsg(500.millis, "ping")
        }
      }
    }
  }

}
