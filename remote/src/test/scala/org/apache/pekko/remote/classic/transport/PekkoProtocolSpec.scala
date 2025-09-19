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

import java.util.concurrent.TimeoutException

import scala.annotation.nowarn
import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Address
import pekko.protobufv3.internal.{ ByteString => PByteString }
import pekko.remote.{ FailureDetector, WireFormats }
import pekko.remote.classic.transport.PekkoProtocolSpec.TestFailureDetector
import pekko.remote.transport.{ AssociationRegistry => _, _ }
import pekko.remote.transport.AssociationHandle.{
  ActorHandleEventListener,
  DisassociateInfo,
  Disassociated,
  InboundPayload
}
import pekko.remote.transport.PekkoPduCodec.{ Associate, Disassociate, Heartbeat }
import pekko.remote.transport.ProtocolStateActor
import pekko.remote.transport.TestTransport._
import pekko.remote.transport.Transport._
import pekko.testkit.{ ImplicitSender, PekkoSpec }
import pekko.util.{ ByteString, OptionVal }

import com.typesafe.config.ConfigFactory

object PekkoProtocolSpec {

  class TestFailureDetector extends FailureDetector {
    @volatile var isAvailable: Boolean = true

    def isMonitoring: Boolean = called

    @volatile var called: Boolean = false

    def heartbeat(): Unit = called = true
  }

}

@nowarn("msg=deprecated")
class PekkoProtocolSpec extends PekkoSpec("""pekko.actor.provider = remote """) with ImplicitSender {

  val conf = ConfigFactory.parseString("""
      pekko.remote {

        
        
        classic {
          backoff-interval = 1 s
          shutdown-timeout = 5 s
          startup-timeout = 5 s
          use-passive-connections = on
          transport-failure-detector {
            implementation-class = "org.apache.pekko.remote.PhiAccrualFailureDetector"
            max-sample-size = 100
            min-std-deviation = 100 ms
            acceptable-heartbeat-pause = 3 s
            heartbeat-interval = 1 s
          }
        }

      }
      # test is using Java serialization and not priority to rewrite
      pekko.actor.allow-java-serialization = on
      pekko.actor.warn-about-java-serializer-usage = off
  """).withFallback(system.settings.config)

  val localAddress = Address("test", "testsystem", "testhost", 1234)
  val localAkkaAddress = Address("pekko.test", "testsystem", "testhost", 1234)

  val remoteAddress = Address("test", "testsystem2", "testhost2", 1234)
  val remoteAkkaAddress = Address("pekko.test", "testsystem2", "testhost2", 1234)

  val codec = PekkoPduProtobufCodec

  val testMsg =
    WireFormats.SerializedMessage.newBuilder().setSerializerId(0).setMessage(PByteString.copyFromUtf8("foo")).build
  val testEnvelope = codec.constructMessage(localAkkaAddress, testActor, testMsg, OptionVal.None)
  val testMsgPdu: ByteString = codec.constructPayload(testEnvelope)

  def testHeartbeat = InboundPayload(codec.constructHeartbeat)
  def testPayload = InboundPayload(testMsgPdu)

  def testDisassociate(info: DisassociateInfo) = InboundPayload(codec.constructDisassociate(info))
  def testAssociate(uid: Int) =
    InboundPayload(codec.constructAssociate(HandshakeInfo(remoteAkkaAddress, uid)))

  def collaborators = {
    val registry = new AssociationRegistry
    val transport: TestTransport = new TestTransport(localAddress, registry)
    val handle: TestAssociationHandle = TestAssociationHandle(localAddress, remoteAddress, transport, true)

    // silently drop writes -- we do not have another endpoint under test, so nobody to forward to
    transport.writeBehavior.pushConstant(true)
    (new TestFailureDetector, registry, transport, handle)
  }

  def lastActivityIsHeartbeat(registry: AssociationRegistry) =
    if (registry.logSnapshot.isEmpty) false
    else
      registry.logSnapshot.last match {
        case WriteAttempt(sender, recipient, payload) if sender == localAddress && recipient == remoteAddress =>
          codec.decodePdu(payload) match {
            case Heartbeat => true
            case _         => false
          }
        case _ => false
      }

  def lastActivityIsAssociate(registry: AssociationRegistry, uid: Long) =
    if (registry.logSnapshot.isEmpty) false
    else
      registry.logSnapshot.last match {
        case WriteAttempt(sender, recipient, payload) if sender == localAddress && recipient == remoteAddress =>
          codec.decodePdu(payload) match {
            case Associate(info) =>
              info.origin == localAddress && info.uid == uid
            case _ => false
          }
        case _ => false
      }

  def lastActivityIsDisassociate(registry: AssociationRegistry) =
    if (registry.logSnapshot.isEmpty) false
    else
      registry.logSnapshot.last match {
        case WriteAttempt(sender, recipient, payload) if sender == localAddress && recipient == remoteAddress =>
          codec.decodePdu(payload) match {
            case Disassociate(_) => true
            case _               => false
          }
        case _ => false
      }

  "ProtocolStateActor" must {

    "register itself as reader on injecteted handles" in {
      val (failureDetector, _, _, handle) = collaborators

      system.actorOf(
        ProtocolStateActor.inboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          handle,
          ActorAssociationEventListener(testActor),
          new PekkoProtocolSettings(conf),
          codec,
          failureDetector))

      awaitCond(handle.readHandlerPromise.isCompleted)
    }

    "in inbound mode accept payload after Associate PDU received" in {
      val (failureDetector, registry, _, handle) = collaborators

      val reader = system.actorOf(
        ProtocolStateActor.inboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          handle,
          ActorAssociationEventListener(testActor),
          new PekkoProtocolSettings(conf),
          codec,
          failureDetector))

      reader ! testAssociate(uid = 33)

      awaitCond(failureDetector.called)

      val wrappedHandle = expectMsgPF() {
        case InboundAssociation(h: PekkoProtocolHandle) =>
          h.handshakeInfo.uid should ===(33)
          h
      }

      wrappedHandle.readHandlerPromise.success(ActorHandleEventListener(testActor))

      failureDetector.called should ===(true)

      // Heartbeat was sent in response to Associate
      awaitCond(lastActivityIsHeartbeat(registry))

      reader ! testPayload

      expectMsgPF() {
        case InboundPayload(p) => p should ===(testEnvelope)
      }
    }

    "in inbound mode disassociate when an unexpected message arrives instead of Associate" in {
      val (failureDetector, registry, _, handle) = collaborators

      val reader = system.actorOf(
        ProtocolStateActor.inboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          handle,
          ActorAssociationEventListener(testActor),
          new PekkoProtocolSettings(conf),
          codec,
          failureDetector))

      // a stray message will force a disassociate
      reader ! testHeartbeat

      // this associate will now be ignored
      reader ! testAssociate(uid = 33)

      awaitCond(registry.logSnapshot.exists {
        case DisassociateAttempt(_, _) => true
        case _                         => false
      })
    }

    "in outbound mode delay readiness until handshake finished" in {
      val (failureDetector, registry, transport, handle) = collaborators
      transport.associateBehavior.pushConstant(handle)

      val statusPromise: Promise[AssociationHandle] = Promise()

      val reader = system.actorOf(
        ProtocolStateActor.outboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          remoteAddress,
          statusPromise,
          transport,
          new PekkoProtocolSettings(conf),
          codec,
          failureDetector,
          refuseUid = None))

      awaitCond(lastActivityIsAssociate(registry, 42))
      awaitCond(failureDetector.called)

      // keeps sending heartbeats
      awaitCond(lastActivityIsHeartbeat(registry))

      statusPromise.isCompleted should ===(false)

      // finish connection by sending back an associate message
      reader ! testAssociate(33)

      Await.result(statusPromise.future, 3.seconds) match {
        case h: PekkoProtocolHandle =>
          h.remoteAddress should ===(remoteAkkaAddress)
          h.localAddress should ===(localAkkaAddress)
          h.handshakeInfo.uid should ===(33)

        case _ => fail()
      }

    }

    "handle explicit disassociate messages" in {
      val (failureDetector, registry, transport, handle) = collaborators
      transport.associateBehavior.pushConstant(handle)

      val statusPromise: Promise[AssociationHandle] = Promise()

      val reader = system.actorOf(
        ProtocolStateActor.outboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          remoteAddress,
          statusPromise,
          transport,
          new PekkoProtocolSettings(conf),
          codec,
          failureDetector,
          refuseUid = None))

      awaitCond(lastActivityIsAssociate(registry, uid = 42))

      reader ! testAssociate(uid = 33)

      val wrappedHandle = Await.result(statusPromise.future, 3.seconds) match {
        case h: AssociationHandle =>
          h.remoteAddress should ===(remoteAkkaAddress)
          h.localAddress should ===(localAkkaAddress)
          h
      }

      wrappedHandle.readHandlerPromise.success(ActorHandleEventListener(testActor))

      reader ! testDisassociate(AssociationHandle.Unknown)

      expectMsg(Disassociated(AssociationHandle.Unknown))
    }

    "handle transport level disassociations" in {
      val (failureDetector, registry, transport, handle) = collaborators
      transport.associateBehavior.pushConstant(handle)

      val statusPromise: Promise[AssociationHandle] = Promise()

      val reader = system.actorOf(
        ProtocolStateActor.outboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          remoteAddress,
          statusPromise,
          transport,
          new PekkoProtocolSettings(conf),
          codec,
          failureDetector,
          refuseUid = None))

      awaitCond(lastActivityIsAssociate(registry, uid = 42))

      reader ! testAssociate(uid = 33)

      val wrappedHandle = Await.result(statusPromise.future, 3.seconds) match {
        case h: AssociationHandle =>
          h.remoteAddress should ===(remoteAkkaAddress)
          h.localAddress should ===(localAkkaAddress)
          h
      }

      wrappedHandle.readHandlerPromise.success(ActorHandleEventListener(testActor))

      reader ! Disassociated(AssociationHandle.Unknown)

      expectMsg(Disassociated(AssociationHandle.Unknown))
    }

    "disassociate when failure detector signals failure" in {
      val (failureDetector, registry, transport, handle) = collaborators
      transport.associateBehavior.pushConstant(handle)

      val statusPromise: Promise[AssociationHandle] = Promise()

      val stateActor = system.actorOf(
        ProtocolStateActor.outboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          remoteAddress,
          statusPromise,
          transport,
          new PekkoProtocolSettings(conf),
          codec,
          failureDetector,
          refuseUid = None))

      awaitCond(lastActivityIsAssociate(registry, uid = 42))

      stateActor ! testAssociate(uid = 33)

      val wrappedHandle = Await.result(statusPromise.future, 3.seconds) match {
        case h: AssociationHandle =>
          h.remoteAddress should ===(remoteAkkaAddress)
          h.localAddress should ===(localAkkaAddress)
          h
      }

      wrappedHandle.readHandlerPromise.success(ActorHandleEventListener(testActor))

      // wait for one heartbeat
      awaitCond(lastActivityIsHeartbeat(registry))

      failureDetector.isAvailable = false

      expectMsg(Disassociated(AssociationHandle.Unknown))
    }

    "handle correctly when the handler is registered only after the association is already closed" in {
      val (failureDetector, registry, transport, handle) = collaborators
      transport.associateBehavior.pushConstant(handle)

      val statusPromise: Promise[AssociationHandle] = Promise()

      val stateActor = system.actorOf(
        ProtocolStateActor.outboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          remoteAddress,
          statusPromise,
          transport,
          new PekkoProtocolSettings(conf),
          codec,
          failureDetector,
          refuseUid = None))

      awaitCond(lastActivityIsAssociate(registry, uid = 42))

      stateActor ! testAssociate(uid = 33)

      val wrappedHandle = Await.result(statusPromise.future, 3.seconds) match {
        case h: AssociationHandle =>
          h.remoteAddress should ===(remoteAkkaAddress)
          h.localAddress should ===(localAkkaAddress)
          h
      }

      stateActor ! Disassociated(AssociationHandle.Unknown)

      wrappedHandle.readHandlerPromise.success(ActorHandleEventListener(testActor))

      expectMsg(Disassociated(AssociationHandle.Unknown))

    }

    "give up outbound after connection timeout" in {
      val (failureDetector, _, transport, handle) = collaborators
      handle.writable = false // nothing will be written
      transport.associateBehavior.pushConstant(handle)

      val statusPromise: Promise[AssociationHandle] = Promise()

      val conf2 =
        ConfigFactory.parseString("pekko.remote.classic.netty.tcp.connection-timeout = 500 ms").withFallback(conf)

      val stateActor = system.actorOf(
        ProtocolStateActor.outboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          remoteAddress,
          statusPromise,
          transport,
          new PekkoProtocolSettings(conf2),
          codec,
          failureDetector,
          refuseUid = None))

      watch(stateActor)
      intercept[TimeoutException] {
        Await.result(statusPromise.future, 5.seconds)
      }
      expectTerminated(stateActor)
    }

    "give up inbound after connection timeout" in {
      val (failureDetector, _, _, handle) = collaborators

      val conf2 =
        ConfigFactory.parseString("pekko.remote.classic.netty.tcp.connection-timeout = 500 ms").withFallback(conf)

      val reader = system.actorOf(
        ProtocolStateActor.inboundProps(
          HandshakeInfo(origin = localAddress, uid = 42),
          handle,
          ActorAssociationEventListener(testActor),
          new PekkoProtocolSettings(conf2),
          codec,
          failureDetector))

      watch(reader)
      expectTerminated(reader)
    }

  }

}
