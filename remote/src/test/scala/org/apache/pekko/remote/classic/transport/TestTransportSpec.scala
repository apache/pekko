/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.classic.transport

import scala.annotation.nowarn
import scala.concurrent._

import org.apache.pekko
import pekko.actor.Address
import pekko.remote.transport.{ AssociationHandle, TestTransport }
import pekko.remote.transport.AssociationHandle.{ ActorHandleEventListener, Disassociated, InboundPayload }
import pekko.remote.transport.TestTransport._
import pekko.remote.transport.Transport._
import pekko.testkit._
import pekko.util.ByteString

@nowarn("msg=deprecated")
class TestTransportSpec extends PekkoSpec with DefaultTimeout with ImplicitSender {

  val addressA: Address = Address("test", "testsytemA", "testhostA", 4321)
  val addressB: Address = Address("test", "testsytemB", "testhostB", 5432)
  val nonExistingAddress = Address("test", "nosystem", "nohost", 0)

  "TestTransport" must {

    "return an Address and promise when listen is called and log calls" in {
      val registry = new AssociationRegistry
      val transportA = new TestTransport(addressA, registry)

      val result = Await.result(transportA.listen, timeout.duration)

      result._1 should ===(addressA)
      result._2 should not be null

      registry.logSnapshot.exists {
        case ListenAttempt(address) => address == addressA
        case _                      => false
      } should ===(true)
    }

    "associate successfully with another TestTransport and log" in {
      val registry = new AssociationRegistry
      val transportA = new TestTransport(addressA, registry)
      val transportB = new TestTransport(addressB, registry)

      // Must complete the returned promise to receive events
      Await.result(transportA.listen, timeout.duration)._2.success(ActorAssociationEventListener(self))
      Await.result(transportB.listen, timeout.duration)._2.success(ActorAssociationEventListener(self))

      awaitCond(registry.transportsReady(addressA, addressB))

      transportA.associate(addressB)
      expectMsgPF(timeout.duration, "Expect InboundAssociation from A") {
        case InboundAssociation(handle) if handle.remoteAddress == addressA =>
      }

      registry.logSnapshot.contains(AssociateAttempt(addressA, addressB)) should ===(true)
    }

    "fail to associate with nonexisting address" in {
      val registry = new AssociationRegistry
      val transportA = new TestTransport(addressA, registry)

      Await.result(transportA.listen, timeout.duration)._2.success(ActorAssociationEventListener(self))

      // TestTransport throws IllegalAssociationException when trying to associate with non-existing system
      intercept[InvalidAssociationException] {
        Await.result(transportA.associate(nonExistingAddress), timeout.duration)
      }

    }

    "emulate sending PDUs and logs write" in {
      val registry = new AssociationRegistry
      val transportA = new TestTransport(addressA, registry)
      val transportB = new TestTransport(addressB, registry)

      Await.result(transportA.listen, timeout.duration)._2.success(ActorAssociationEventListener(self))
      Await.result(transportB.listen, timeout.duration)._2.success(ActorAssociationEventListener(self))

      awaitCond(registry.transportsReady(addressA, addressB))

      val associate: Future[AssociationHandle] = transportA.associate(addressB)
      val handleB = expectMsgPF(timeout.duration, "Expect InboundAssociation from A") {
        case InboundAssociation(handle) if handle.remoteAddress == addressA => handle
      }

      handleB.readHandlerPromise.success(ActorHandleEventListener(self))
      val handleA = Await.result(associate, timeout.duration)

      // Initialize handles
      handleA.readHandlerPromise.success(ActorHandleEventListener(self))

      val pekkoPDU = ByteString("PekkoPDU")

      awaitCond(registry.existsAssociation(addressA, addressB))

      handleA.write(pekkoPDU)
      expectMsgPF(timeout.duration, "Expect InboundPayload from A") {
        case InboundPayload(payload) if payload == pekkoPDU =>
      }

      registry.logSnapshot.exists {
        case WriteAttempt(sender, recipient, payload) =>
          sender == addressA && recipient == addressB && payload == pekkoPDU
        case _ => false
      } should ===(true)
    }

    "emulate disassociation and log it" in {
      val registry = new AssociationRegistry
      val transportA = new TestTransport(addressA, registry)
      val transportB = new TestTransport(addressB, registry)

      Await.result(transportA.listen, timeout.duration)._2.success(ActorAssociationEventListener(self))
      Await.result(transportB.listen, timeout.duration)._2.success(ActorAssociationEventListener(self))

      awaitCond(registry.transportsReady(addressA, addressB))

      val associate: Future[AssociationHandle] = transportA.associate(addressB)
      val handleB: AssociationHandle = expectMsgPF(timeout.duration, "Expect InboundAssociation from A") {
        case InboundAssociation(handle) if handle.remoteAddress == addressA => handle
      }

      handleB.readHandlerPromise.success(ActorHandleEventListener(self))
      val handleA = Await.result(associate, timeout.duration)

      // Initialize handles
      handleA.readHandlerPromise.success(ActorHandleEventListener(self))

      awaitCond(registry.existsAssociation(addressA, addressB))

      handleA.disassociate("Test disassociation", log)

      expectMsgPF(timeout.duration) {
        case Disassociated(_) =>
      }

      awaitCond(!registry.existsAssociation(addressA, addressB))

      registry.logSnapshot.exists {
        case DisassociateAttempt(requester, remote) if requester == addressA && remote == addressB => true
        case _                                                                                     => false
      } should ===(true)
    }

  }

}
