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
import scala.concurrent.duration._

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

class OutboundIdleShutdownSpec extends ArteryMultiNodeSpec(s"""
  pekko.loglevel=INFO
  pekko.remote.artery.advanced.stop-idle-outbound-after = 1 s
  pekko.remote.artery.advanced.connection-timeout = 2 s
  pekko.remote.artery.advanced.remove-quarantined-association-after = 1 s
  pekko.remote.artery.advanced.compression {
    actor-refs.advertisement-interval = 5 seconds
  }
  """) with ImplicitSender with Eventually {

  override implicit val patience: PatienceConfig = {
    import pekko.testkit.TestDuration
    PatienceConfig(testKitSettings.DefaultTimeout.duration.dilated * 2, Span(200, org.scalatest.time.Millis))
  }

  private def isArteryTcp: Boolean =
    RARP(system).provider.transport.asInstanceOf[ArteryTransport].settings.Transport == ArterySettings.Tcp

  private def assertStreamActive(association: Association, queueIndex: Int, expected: Boolean): Unit = {
    if (queueIndex == Association.ControlQueueIndex) {
      // the control stream is not stopped, but for TCP the connection is closed
      if (expected)
        association.isStreamActive(queueIndex) shouldBe expected
      else if (isArteryTcp && !association.isRemovedAfterQuarantined()) {
        association.associationState.controlIdleKillSwitch.isDefined shouldBe expected
      }
    } else {
      association.isStreamActive(queueIndex) shouldBe expected
    }

  }

  private def futureUniqueRemoteAddress(association: Association): Future[UniqueAddress] = {
    val p = Promise[UniqueAddress]()
    association.associationState.addUniqueRemoteAddressListener(a => p.success(a))
    p.future
  }

  "Outbound streams" should {

    "be stopped when they are idle" in withAssociation { (_, remoteAddress, _, localArtery, _) =>
      val association = localArtery.association(remoteAddress)
      withClue("When initiating a connection, both the control and ordinary streams are opened") {
        assertStreamActive(association, Association.ControlQueueIndex, expected = true)
        assertStreamActive(association, Association.OrdinaryQueueIndex, expected = true)
      }

      eventually {
        assertStreamActive(association, Association.ControlQueueIndex, expected = false)
        assertStreamActive(association, Association.OrdinaryQueueIndex, expected = false)
      }
    }

    "still be resumable after they have been stopped" in withAssociation {
      (_, remoteAddress, remoteEcho, localArtery, localProbe) =>
        val firstAssociation = localArtery.association(remoteAddress)

        eventually {
          assertStreamActive(firstAssociation, Association.ControlQueueIndex, expected = false)
          assertStreamActive(firstAssociation, Association.OrdinaryQueueIndex, expected = false)
        }

        withClue("re-initiating the connection should be the same as starting it the first time") {

          eventually {
            remoteEcho.tell("ping", localProbe.ref)
            localProbe.expectMsg("ping")
            val secondAssociation = localArtery.association(remoteAddress)
            assertStreamActive(secondAssociation, Association.ControlQueueIndex, expected = true)
            assertStreamActive(secondAssociation, Association.OrdinaryQueueIndex, expected = true)
          }

        }
    }

    "eliminate quarantined association when not used" in withAssociation { (_, remoteAddress, _, localArtery, _) =>
      val association = localArtery.association(remoteAddress)
      withClue("When initiating a connection, both the control and ordinary streams are opened") {
        assertStreamActive(association, Association.ControlQueueIndex, expected = true)
        assertStreamActive(association, Association.OrdinaryQueueIndex, expected = true)
      }

      val remoteUid = futureUniqueRemoteAddress(association).futureValue.uid

      localArtery.quarantine(remoteAddress, Some(remoteUid), "Test")
      association.associationState.isQuarantined(remoteUid) shouldBe true
      association.associationState.quarantinedButHarmless(remoteUid) shouldBe false

      eventually {
        assertStreamActive(association, Association.ControlQueueIndex, expected = false)
        assertStreamActive(association, Association.OrdinaryQueueIndex, expected = false)
      }

      // the outbound streams are inactive and association quarantined, then it's completely removed
      eventually {
        localArtery.remoteAddresses should not contain remoteAddress
      }
    }

    "eliminate quarantined association when not used (harmless=true)" in withAssociation {
      (_, remoteAddress, _, localArtery, _) =>
        val association = localArtery.association(remoteAddress)
        val remoteUid = futureUniqueRemoteAddress(association).futureValue.uid

        localArtery.quarantine(remoteAddress, Some(remoteUid), "HarmlessTest", harmless = true)
        association.associationState.isQuarantined(remoteUid) shouldBe true
        association.associationState.quarantinedButHarmless(remoteUid) shouldBe true

        eventually {
          assertStreamActive(association, Association.ControlQueueIndex, expected = false)
          assertStreamActive(association, Association.OrdinaryQueueIndex, expected = false)
        }

        // the outbound streams are inactive and association quarantined, then it's completely removed
        eventually {
          localArtery.remoteAddresses should not contain remoteAddress
        }
    }

    "remove inbound compression after quarantine" in withAssociation { (_, remoteAddress, _, localArtery, _) =>
      val association = localArtery.association(remoteAddress)
      val remoteUid = futureUniqueRemoteAddress(association).futureValue.uid

      localArtery.inboundCompressionAccess.get.currentCompressionOriginUids.futureValue should contain(remoteUid)

      eventually {
        assertStreamActive(association, Association.OrdinaryQueueIndex, expected = false)
      }
      // compression still exists when idle
      localArtery.inboundCompressionAccess.get.currentCompressionOriginUids.futureValue should contain(remoteUid)

      localArtery.quarantine(remoteAddress, Some(remoteUid), "Test")
      // after quarantine it should be removed
      eventually {
        localArtery.inboundCompressionAccess.get.currentCompressionOriginUids.futureValue should not contain remoteUid
      }
    }

    "remove inbound compression after restart with same host:port" in withAssociation {
      (remoteSystem, remoteAddress, _, localArtery, localProbe) =>
        val association = localArtery.association(remoteAddress)
        val remoteUid = futureUniqueRemoteAddress(association).futureValue.uid

        localArtery.inboundCompressionAccess.get.currentCompressionOriginUids.futureValue should contain(remoteUid)

        shutdown(remoteSystem, verifySystemShutdown = true)

        val remoteSystem2 = newRemoteSystem(
          Some(s"""
          pekko.remote.artery.canonical.hostname = ${remoteAddress.host.get}
          pekko.remote.artery.canonical.port = ${remoteAddress.port.get}
          """),
          name = Some(remoteAddress.system))
        try {

          remoteSystem2.actorOf(TestActors.echoActorProps, "echo2")

          def remoteEcho = system.actorSelection(RootActorPath(remoteAddress) / "user" / "echo2")

          val echoRef = eventually {
            remoteEcho.resolveOne(1.seconds).futureValue
          }

          echoRef.tell("ping2", localProbe.ref)
          localProbe.expectMsg("ping2")

          val association2 = localArtery.association(remoteAddress)
          val remoteUid2 = futureUniqueRemoteAddress(association2).futureValue.uid

          remoteUid2 should !==(remoteUid)

          eventually {
            localArtery.inboundCompressionAccess.get.currentCompressionOriginUids.futureValue should contain(remoteUid2)
          }
          eventually {
            localArtery.inboundCompressionAccess.get.currentCompressionOriginUids.futureValue should not contain remoteUid
          }
        } finally {
          shutdown(remoteSystem2)
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
