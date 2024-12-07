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

import scala.concurrent.Future
import scala.concurrent.Promise

import org.scalatest.concurrent.Eventually
import org.scalatest.time.Span

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.actor.RootActorPath
import pekko.remote.RARP
import pekko.remote.UniqueAddress
import pekko.testkit.ImplicitSender
import pekko.testkit.TestActors
import pekko.testkit.TestProbe

class HarmlessQuarantineSpec extends ArteryMultiNodeSpec("""
  pekko.loglevel=INFO
  pekko.remote.artery.propagate-harmless-quarantine-events = off
  pekko.remote.artery.advanced {
    stop-idle-outbound-after = 1 s
    connection-timeout = 2 s
    remove-quarantined-association-after = 1 s
    compression {
      actor-refs.advertisement-interval = 5 seconds
    }
  }
  """) with ImplicitSender with Eventually {

  override implicit val patience: PatienceConfig = {
    import pekko.testkit.TestDuration
    PatienceConfig(testKitSettings.DefaultTimeout.duration.dilated * 2, Span(200, org.scalatest.time.Millis))
  }

  private def futureUniqueRemoteAddress(association: Association): Future[UniqueAddress] = {
    val p = Promise[UniqueAddress]()
    association.associationState.addUniqueRemoteAddressListener(a => p.success(a))
    p.future
  }

  "Harmless Quarantine Events" should {

    "eliminate quarantined association when not used - echo test" in withAssociation {
      (remoteSystem, remoteAddress, _, localArtery, localProbe) =>
        // event to watch out for, indicator of the issue
        remoteSystem.eventStream.subscribe(testActor, classOf[ThisActorSystemQuarantinedEvent])

        val remoteEcho = remoteSystem.actorSelection("/user/echo").resolveOne(remainingOrDefault).futureValue

        val localAddress = RARP(system).provider.getDefaultAddress

        val localEchoRef =
          remoteSystem.actorSelection(RootActorPath(localAddress) / localProbe.ref.path.elements).resolveOne(
            remainingOrDefault).futureValue
        remoteEcho.tell("ping", localEchoRef)
        localProbe.expectMsg("ping")

        val association = localArtery.association(remoteAddress)
        val remoteUid = futureUniqueRemoteAddress(association).futureValue.uid
        localArtery.quarantine(remoteAddress, Some(remoteUid), "Test")
        association.associationState.isQuarantined(remoteUid) shouldBe true
        association.associationState.quarantinedButHarmless(remoteUid) shouldBe false

        remoteEcho.tell("ping", localEchoRef) // trigger sending message from remote to local, which will trigger local to wrongfully notify remote that it is quarantined
        eventually {
          expectMsgType[ThisActorSystemQuarantinedEvent] // this is what remote emits when it learns it is quarantined by local
        }
    }

    "eliminate quarantined association when not used - echo test (harmless=true)" in withAssociation {
      (remoteSystem, remoteAddress, _, localArtery, localProbe) =>
        // event to watch out for, indicator of the issue
        remoteSystem.eventStream.subscribe(testActor, classOf[ThisActorSystemQuarantinedEvent])

        val remoteEcho = remoteSystem.actorSelection("/user/echo").resolveOne(remainingOrDefault).futureValue

        val localAddress = RARP(system).provider.getDefaultAddress

        val localEchoRef =
          remoteSystem.actorSelection(RootActorPath(localAddress) / localProbe.ref.path.elements).resolveOne(
            remainingOrDefault).futureValue
        remoteEcho.tell("ping", localEchoRef)
        localProbe.expectMsg("ping")

        val association = localArtery.association(remoteAddress)
        val remoteUid = futureUniqueRemoteAddress(association).futureValue.uid
        localArtery.quarantine(remoteAddress, Some(remoteUid), "HarmlessTest", harmless = true)
        association.associationState.isQuarantined(remoteUid) shouldBe true
        association.associationState.quarantinedButHarmless(remoteUid) shouldBe true

        remoteEcho.tell("ping", localEchoRef) // trigger sending message from remote to local, which will trigger local to wrongfully notify remote that it is quarantined
        eventually {
          expectNoMessage()
        }
    }

    /**
     * Test setup fixture:
     * 1. A 'remote' ActorSystem is created to spawn an Echo actor,
     * 2. A TestProbe is spawned locally to initiate communication with the Echo actor
     * 3. Details (remoteAddress, remoteEcho, localArtery, localProbe) are supplied to the test
     */
    def withAssociation(test: (ActorSystem, Address, ActorRef, ArteryTransport, TestProbe) => Any): Unit = {
      val remoteSystem = newRemoteSystem()
      try {
        remoteSystem.actorOf(TestActors.echoActorProps, "echo")
        val remoteAddress = RARP(remoteSystem).provider.getDefaultAddress

        def remoteEcho = system.actorSelection(RootActorPath(remoteAddress) / "user" / "echo")

        val echoRef = remoteEcho.resolveOne(remainingOrDefault).futureValue
        val localProbe = new TestProbe(localSystem)

        echoRef.tell("ping", localProbe.ref)
        localProbe.expectMsg("ping")

        val artery = RARP(system).provider.transport.asInstanceOf[ArteryTransport]

        test(remoteSystem, remoteAddress, echoRef, artery, localProbe)

      } finally {
        shutdown(remoteSystem)
      }
    }
  }
}
