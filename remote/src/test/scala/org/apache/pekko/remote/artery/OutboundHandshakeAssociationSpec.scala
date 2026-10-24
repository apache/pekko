/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.remote.artery

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.AllDeadLetters
import pekko.actor.ExtendedActorSystem
import pekko.dispatch.sysmsg.DeathWatchNotification
import pekko.remote.RARP
import pekko.remote.RemoteActorRefProvider
import pekko.remote.UniqueAddress
import pekko.remote.artery.Encoder.OutboundCompressionAccess
import pekko.remote.artery.OutboundHandshake.HandshakeReq
import pekko.stream.Attributes
import pekko.stream.FlowShape
import pekko.stream.Inlet
import pekko.stream.Outlet
import pekko.stream.scaladsl.Flow
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.EventFilter
import pekko.testkit.ExplicitlyTriggeredScheduler
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe
import pekko.util.OptionVal

import com.typesafe.config.ConfigFactory

class OutboundHandshakeAssociationSpec
    extends PekkoSpec(ConfigFactory.parseString("""
      pekko.scheduler.implementation = "org.apache.pekko.testkit.ExplicitlyTriggeredScheduler"
      pekko.remote.artery.advanced {
        use-dispatcher = "pekko.test.calling-thread-dispatcher"
        use-control-stream-dispatcher = "pekko.test.calling-thread-dispatcher"
        inject-handshake-interval = 1 s
        system-message-resend-interval = 1 hour
        shutdown-flush-timeout = 1 ms
      }
    """).withFallback(ArterySpecSupport.defaultConfig)) {

  private val peer = UniqueAddress(pekko.actor.Address("pekko", "peer", "127.0.0.1", 1002), 2L)

  private def advanceTime(): Unit =
    system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler].timePasses(1.second)

  private case class Output(streamId: Int, downstream: TestSubscriber.Probe[EnvelopeBuffer])
  private case class Encoded(streamId: Int, message: AnyRef)

  private class ControlledTransport(deferFirstCompletion: Boolean, failFirstStart: Boolean)
      extends ArteryTransport(
        system.asInstanceOf[ExtendedActorSystem],
        RARP(system).provider.asInstanceOf[RemoteActorRefProvider]) {
    override type LifeCycle = Unit

    val outputs = TestProbe()(system)
    val encoded = TestProbe()(system)
    val completionGate = Promise[Done]()
    private var firstControl = true
    private var failStart = failFirstStart

    override protected def startTransport(): Unit = ()
    override protected def bindInboundStreams(): (Int, Int) = (1001, 1001)
    override protected def runInboundStreams(port: Int, bindPort: Int): Unit =
      attachControlMessageObserver(new TestControlMessageSubject)
    override protected def shutdownTransport(): Future[Done] = Future.successful(Done)

    def stopStreams(): Unit = killSwitch.abort(ArteryTransport.ShutdownSignal)

    override def createEncoder(
        pool: EnvelopeBufferPool,
        streamId: Int): Flow[OutboundEnvelope, EnvelopeBuffer, OutboundCompressionAccess] =
      Flow[OutboundEnvelope]
        .map { envelope =>
          encoded.ref ! Encoded(streamId, envelope.message)
          envelope
        }
        .viaMat(super.createEncoder(pool, streamId))(Keep.right)

    override def outboundTestFlow(context: OutboundContext): Flow[OutboundEnvelope, OutboundEnvelope, NotUsed] =
      if (failStart) {
        failStart = false
        Flow.fromGraph(new GraphStage[FlowShape[OutboundEnvelope, OutboundEnvelope]] {
          val in = Inlet[OutboundEnvelope]("failStart.in")
          val out = Outlet[OutboundEnvelope]("failStart.out")
          override val shape = FlowShape(in, out)
          override def createLogic(attributes: Attributes): GraphStageLogic =
            new GraphStageLogic(shape) with InHandler with OutHandler {
              override def preStart(): Unit = throw new IllegalStateException("control initialization failed")
              override def onPush(): Unit = push(out, grab(in))
              override def onPull(): Unit = pull(in)
              setHandlers(in, out, this)
            }
        })
      } else super.outboundTestFlow(context)

    override protected def outboundTransportSink(
        context: OutboundContext,
        streamId: Int,
        pool: EnvelopeBufferPool): Sink[EnvelopeBuffer, Future[Done]] = {
      val deferCompletion = streamId == ArteryTransport.ControlStreamId && firstControl && deferFirstCompletion
      if (streamId == ArteryTransport.ControlStreamId) firstControl = false
      Flow[EnvelopeBuffer]
        .watchTermination(Keep.right)
        .toMat(TestSink[EnvelopeBuffer]()(system))(Keep.both)
        .mapMaterializedValue { case (completed, downstream) =>
          outputs.ref ! Output(streamId, downstream)
          if (deferCompletion)
            completed.transformWith(result => completionGate.future.transform(_ => result)(system.dispatcher))(
              system.dispatcher)
          else completed
        }
    }

    def receive(output: Output): AnyRef = {
      output.downstream.request(1)
      val buffer = output.downstream.expectNext()
      envelopeBufferPool.release(buffer)
      val message = encoded.expectMsgType[Encoded]
      message.streamId should ===(output.streamId)
      message.message
    }

    def expectInjectedHandshake(output: Output): Unit = {
      val buffer = output.downstream.expectNext()
      envelopeBufferPool.release(buffer)
      encoded.expectMsg(Encoded(output.streamId, HandshakeReq(localAddress, peer.address)))
    }
  }

  private class Fixture(deferFirstCompletion: Boolean = false, failFirstStart: Boolean = false) {
    val transport = new ControlledTransport(deferFirstCompletion, failFirstStart)
    val deadLetters = TestProbe()
    val quarantines = TestProbe()
    system.eventStream.subscribe(deadLetters.ref, classOf[AllDeadLetters])
    system.eventStream.subscribe(quarantines.ref, classOf[QuarantinedEvent])
    system.eventStream.subscribe(quarantines.ref, classOf[GracefulShutdownQuarantinedEvent])
    transport.start()
    val association = transport.association(peer.address)
    val outputs = Vector.fill(2)(transport.outputs.expectMsgType[Output])
    val control = outputs.find(_.streamId == ArteryTransport.ControlStreamId).get
    val ordinary = outputs.find(_.streamId == ArteryTransport.OrdinaryStreamId).get

    def hold(message: AnyRef, output: Output = control): Unit = {
      association.completeHandshake(peer).futureValue
      transport.receive(output) should ===(HandshakeReq(transport.localAddress, peer.address))
      holdAfterHandshake(message, output)
    }

    def holdAfterHandshake(message: AnyRef, output: Output = control): Unit = {
      advanceTime()
      output.downstream.request(1)
      association.send(message, OptionVal.None, OptionVal.None)
      transport.expectInjectedHandshake(output)
    }

    def expectDeadLetter(message: AnyRef): Unit = expectDeadLetters(Set(message))

    def expectDeadLetters(messages: Set[Any]): Unit = {
      var remaining = messages
      deadLetters.fishForMessage(hint = "pending envelopes in dead letters") {
        case letter: AllDeadLetters =>
          remaining -= letter.message
          remaining.isEmpty
        case _ => false
      }
    }

    def shutdownTransport(): Unit = {
      val completed = transport.shutdown()
      transport.stopStreams()
      awaitAssert {
        advanceTime()
        completed.isCompleted should ===(true)
      }
      completed.futureValue should ===(Done)
    }

    def close(): Unit = {
      transport.completionGate.trySuccess(Done)
      try shutdownTransport()
      finally {
        system.eventStream.unsubscribe(deadLetters.ref)
        system.eventStream.unsubscribe(quarantines.ref)
      }
    }
  }

  "Pending messages in a real Association" must {
    "quarantine after replacing the stopped control stream and send its notification on the new stream" in {
      val f = new Fixture
      try {
        val message = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        f.hold(message)
        f.control.downstream.cancel()
        f.expectDeadLetter(message)
        f.quarantines.expectMsg(QuarantinedEvent(peer))
        f.association.associationState.isQuarantined(peer.uid) should ===(true)

        val restarted = f.transport.outputs.expectMsgType[Output]
        restarted.streamId should ===(ArteryTransport.ControlStreamId)
        val first = f.transport.receive(restarted)
        if (first.isInstanceOf[HandshakeReq])
          f.transport.receive(restarted) should ===(Quarantined(f.transport.localAddress, peer))
        else first should ===(Quarantined(f.transport.localAddress, peer))
      } finally f.close()
    }

    "send quarantine on the replacement stream when delivery already quarantined the stopped stream" in {
      val f = new Fixture
      try {
        f.association.completeHandshake(peer).futureValue
        f.transport.receive(f.control) should ===(HandshakeReq(f.transport.localAddress, peer.address))
        val delivered = DeathWatchNotification(testActor, existenceConfirmed = false, addressTerminated = false)
        f.association.send(delivered, OptionVal.None, OptionVal.None)
        f.transport.receive(f.control).asInstanceOf[SystemMessageDelivery.SystemMessageEnvelope].message should ===(
          delivered)

        val held = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        f.holdAfterHandshake(held)
        f.control.downstream.cancel()
        f.expectDeadLetters(Set(delivered, held))
        f.quarantines.expectMsg(QuarantinedEvent(peer))
        val restarted = f.transport.outputs.expectMsgType[Output]
        val first = f.transport.receive(restarted)
        if (first.isInstanceOf[HandshakeReq])
          f.transport.receive(restarted) should ===(Quarantined(f.transport.localAddress, peer))
        else first should ===(Quarantined(f.transport.localAddress, peer))
      } finally f.close()
    }

    "report a held system message without quarantine during orderly transport shutdown" in {
      val f = new Fixture
      try {
        val message = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        f.hold(message)
        val queued = DeathWatchNotification(testActor, existenceConfirmed = false, addressTerminated = false)
        f.association.send(queued, OptionVal.None, OptionVal.None)
        f.shutdownTransport()
        f.expectDeadLetters(Set(message, queued))
        f.association.associationState.isQuarantined() should ===(false)
        f.quarantines.expectNoMessage()
      } finally f.close()
    }

    "only report dead letters when an ordinary lane stops with a held message" in {
      val f = new Fixture
      try {
        val message = "ordinary message"
        f.hold(message, f.ordinary)
        f.ordinary.downstream.cancel()
        f.expectDeadLetter(message)
        f.association.associationState.isQuarantined() should ===(false)
        f.quarantines.expectNoMessage()
      } finally f.close()
    }

    "not quarantine when a known control stream stops without pending system messages" in {
      val f = new Fixture
      try {
        f.association.completeHandshake(peer).futureValue
        f.transport.receive(f.control) should ===(HandshakeReq(f.transport.localAddress, peer.address))
        f.control.downstream.cancel()
        awaitAssert {
          f.association.isStreamActive(Association.ControlQueueIndex) should ===(false)
        }
        f.association.associationState.isQuarantined() should ===(false)
        f.quarantines.expectNoMessage()
      } finally f.close()
    }

    "only report dead letters when the remote UID is still unknown" in {
      val f = new Fixture
      try {
        f.transport.receive(f.control) should ===(HandshakeReq(f.transport.localAddress, peer.address))
        val message = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        f.association.send(message, OptionVal.None, OptionVal.None)
        f.control.downstream.cancel()
        f.expectDeadLetter(message)
        awaitAssert {
          f.association.isStreamActive(Association.ControlQueueIndex) should ===(false)
        }
        f.association.associationState.uniqueRemoteAddress() should ===(None)
        f.association.associationState.isQuarantined() should ===(false)
        f.quarantines.expectNoMessage()
      } finally f.close()
    }

    "account for a message held immediately after learning the first remote UID" in {
      val f = new Fixture
      try {
        f.transport.receive(f.control) should ===(HandshakeReq(f.transport.localAddress, peer.address))
        val message = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        f.association.send(message, OptionVal.None, OptionVal.None)
        advanceTime()
        f.control.downstream.request(1)
        f.association.completeHandshake(peer).futureValue
        f.transport.expectInjectedHandshake(f.control)
        f.control.downstream.cancel()
        f.expectDeadLetter(message)
        f.quarantines.expectMsg(QuarantinedEvent(peer))
        f.association.associationState.isQuarantined(peer.uid) should ===(true)
      } finally f.close()
    }

    "not attribute a held message to a new UID learned before the stream stops" in {
      val f = new Fixture
      try {
        val message = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        f.hold(message)
        val nextPeer = peer.copy(uid = peer.uid + 1)
        f.association.completeHandshake(nextPeer).futureValue
        f.control.downstream.cancel()
        f.expectDeadLetter(message)
        awaitAssert {
          f.association.isStreamActive(Association.ControlQueueIndex) should ===(false)
        }
        f.association.associationState.isQuarantined(nextPeer.uid) should ===(false)
        f.quarantines.expectNoMessage()
      } finally f.close()
    }

    "quarantine a new incarnation for a message held after its handshake" in {
      val f = new Fixture
      try {
        f.association.completeHandshake(peer).futureValue
        f.transport.receive(f.control) should ===(HandshakeReq(f.transport.localAddress, peer.address))
        val nextPeer = peer.copy(uid = peer.uid + 1)
        f.association.completeHandshake(nextPeer).futureValue
        val message = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        f.holdAfterHandshake(message)
        f.control.downstream.cancel()
        f.expectDeadLetter(message)
        f.quarantines.expectMsg(QuarantinedEvent(nextPeer))
        f.association.associationState.isQuarantined(nextPeer.uid) should ===(true)
        f.association.associationState.isQuarantined(peer.uid) should ===(false)
      } finally f.close()
    }

    "not quarantine a newer UID when the old stream completion is delayed" in {
      val f = new Fixture(deferFirstCompletion = true)
      try {
        val message = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        f.hold(message)
        f.control.downstream.cancel()
        f.expectDeadLetter(message)
        val nextPeer = peer.copy(uid = peer.uid + 1)
        f.association.completeHandshake(nextPeer).futureValue
        f.transport.completionGate.success(Done)
        awaitAssert {
          f.association.isStreamActive(Association.ControlQueueIndex) should ===(false)
        }
        f.association.associationState.isQuarantined(nextPeer.uid) should ===(false)
        f.quarantines.expectNoMessage()
      } finally f.close()
    }

    "finish both cleanup callbacks after control stream initialization fails" in {
      EventFilter[IllegalStateException](occurrences = 1).intercept {
        val f = new Fixture(failFirstStart = true)
        try {
          f.control.downstream.ensureSubscription().expectError().getMessage should ===("control initialization failed")
          awaitAssert {
            f.association.isStreamActive(Association.ControlQueueIndex) should ===(false)
          }
          f.association.sendControl(HandshakeReq(f.transport.localAddress, peer.address))
          val restarted = f.transport.outputs.expectMsgType[Output]
          f.transport.receive(restarted) should ===(HandshakeReq(f.transport.localAddress, peer.address))
          f.association.associationState.isQuarantined() should ===(false)
        } finally f.close()
      }
    }
  }
}
