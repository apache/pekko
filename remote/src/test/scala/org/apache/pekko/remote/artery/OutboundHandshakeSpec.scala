/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.Address
import pekko.actor.DeadLetter
import pekko.dispatch.sysmsg.DeathWatchNotification
import pekko.remote.UniqueAddress
import pekko.remote.artery.OutboundHandshake.HandshakeReq
import pekko.remote.artery.OutboundHandshake.HandshakeTimeoutException
import pekko.stream.scaladsl.Keep
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource
import pekko.testkit.EventFilter
import pekko.testkit.ExplicitlyTriggeredScheduler
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe
import pekko.util.OptionVal

abstract class OutboundHandshakeSpecBase(config: String) extends PekkoSpec(config) with ImplicitSender {

  val addressA = UniqueAddress(Address("pekko", "sysA", "hostA", 1001), 1)
  val addressB = UniqueAddress(Address("pekko", "sysB", "hostB", 1002), 2)

  private val outboundEnvelopePool = ReusableOutboundEnvelope.createObjectPool(capacity = 16)

  protected def setupStream(
      outboundContext: OutboundContext,
      timeout: FiniteDuration = 5.seconds,
      retryInterval: FiniteDuration = 10.seconds,
      injectHandshakeInterval: FiniteDuration = 10.seconds,
      livenessProbeInterval: Duration = Duration.Undefined,
      postStopAction: Vector[OutboundEnvelope] => Unit = pending => pending.foreach(system.deadLetters ! _))
      : (TestPublisher.Probe[AnyRef], TestSubscriber.Probe[Any]) = {

    TestSource[AnyRef]()
      .map(msg => outboundEnvelopePool.acquire().init(OptionVal.None, msg, OptionVal.None))
      .via(
        new OutboundHandshake(
          system,
          outboundContext,
          outboundEnvelopePool,
          timeout,
          retryInterval,
          injectHandshakeInterval,
          livenessProbeInterval,
          (pending, _) => postStopAction(pending)))
      .map(env => env.message)
      .toMat(TestSink[Any]())(Keep.both)
      .run()
  }
}

class OutboundHandshakeSpec extends OutboundHandshakeSpecBase("""
    pekko.stream.materializer.debug.fuzzing-mode = on
  """) {

  "OutboundHandshake stage" must {
    "send HandshakeReq when first pulled" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      val (_, downstream) = setupStream(outboundContext)

      downstream.request(10)
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.cancel()
    }

    "send HandshakeReq also when uniqueRemoteAddress future completed at startup" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      inboundContext.completeHandshake(addressB)
      val (upstream, downstream) = setupStream(outboundContext)

      upstream.sendNext("msg1")
      downstream.request(10)
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.expectNext("msg1")
      downstream.cancel()
    }

    "timeout if handshake not completed" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      val (_, downstream) = setupStream(outboundContext, timeout = 200.millis)

      downstream.request(1)
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.expectError().getClass should be(classOf[HandshakeTimeoutException])
    }

    "retry HandshakeReq" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      val (_, downstream) = setupStream(outboundContext, retryInterval = 100.millis)

      downstream.request(10)
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.cancel()
    }

    "not deliver messages from upstream until handshake completed" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      val (upstream, downstream) = setupStream(outboundContext)

      downstream.request(10)
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      upstream.sendNext("msg1")
      downstream.expectNoMessage(200.millis)
      // InboundHandshake stage will complete the handshake when receiving HandshakeRsp
      inboundContext.completeHandshake(addressB)
      downstream.expectNext("msg1")
      upstream.sendNext("msg2")
      downstream.expectNext("msg2")
      downstream.cancel()
    }

    "inject HandshakeReq" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      val (upstream, downstream) = setupStream(outboundContext, injectHandshakeInterval = 500.millis)

      downstream.request(10)
      upstream.sendNext("msg1")
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      inboundContext.completeHandshake(addressB)
      downstream.expectNext("msg1")

      downstream.expectNoMessage(600.millis)
      upstream.sendNext("msg2")
      upstream.sendNext("msg3")
      upstream.sendNext("msg4")
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.expectNext("msg2")
      downstream.expectNext("msg3")
      downstream.expectNext("msg4")
      downstream.expectNoMessage(600.millis)

      downstream.cancel()
    }

    "send HandshakeReq for liveness probing" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      val (_, downstream) = setupStream(outboundContext, livenessProbeInterval = 200.millis)

      downstream.request(10)
      // this is from the initial
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      inboundContext.completeHandshake(addressB)
      // these are from  livenessProbeInterval
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.expectNext(HandshakeReq(addressA, addressB.address))
      downstream.cancel()
    }

  }
}

class OutboundHandshakePendingMessageSpec extends OutboundHandshakeSpecBase("""
    pekko.scheduler.implementation = "org.apache.pekko.testkit.ExplicitlyTriggeredScheduler"
    pekko.stream.materializer.dispatcher = "pekko.test.calling-thread-dispatcher"
  """) {

  private def advanceHandshakeTimer(): Unit =
    system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler].timePasses(1.second)

  "OutboundHandshake stage" must {
    "send pending message to deadLetters when stopped while holding it" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      inboundContext.completeHandshake(addressB)
      val (upstream, downstream) = setupStream(outboundContext, injectHandshakeInterval = 1.second)

      system.eventStream.subscribe(testActor, classOf[DeadLetter])
      try {
        downstream.request(1)
        downstream.expectNext(HandshakeReq(addressA, addressB.address))
        // The calling-thread stream dispatcher handles the scheduled callback
        // before timePasses returns, so the next message is held behind a handshake.
        advanceHandshakeTimer()

        downstream.request(1)
        upstream.sendNext("msg1")
        downstream.expectNext(HandshakeReq(addressA, addressB.address))
        downstream.cancel()

        fishForMessage(hint = "envelope with msg1 in deadLetters") {
          case DeadLetter(env: OutboundEnvelope, _, _) => env.message == "msg1"
          case _                                       => false
        }
        outboundContext.associationState.isQuarantined() should ===(false)
      } finally {
        downstream.cancel()
        system.eventStream.unsubscribe(testActor, classOf[DeadLetter])
      }
    }

    "pass a pending system message to the supplied stop handler" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)
      inboundContext.completeHandshake(addressB)
      val stopped = TestProbe()
      val (upstream, downstream) = setupStream(
        outboundContext,
        injectHandshakeInterval = 1.second,
        postStopAction = pending => {
          pending.foreach(system.deadLetters ! _)
          stopped.ref ! pending
        })

      system.eventStream.subscribe(testActor, classOf[DeadLetter])
      try {
        downstream.request(1)
        downstream.expectNext(HandshakeReq(addressA, addressB.address))
        advanceHandshakeTimer()

        val sysMsg = DeathWatchNotification(testActor, existenceConfirmed = true, addressTerminated = false)
        downstream.request(1)
        upstream.sendNext(sysMsg)
        downstream.expectNext(HandshakeReq(addressA, addressB.address))
        downstream.cancel()

        fishForMessage(hint = "envelope with the system message in deadLetters") {
          case DeadLetter(env: OutboundEnvelope, _, _) => env.message eq sysMsg
          case _                                       => false
        }
        stopped.expectMsgType[Vector[OutboundEnvelope]].map(_.message) should ===(Vector(sysMsg))
        outboundContext.associationState.isQuarantined() should ===(false)
      } finally {
        downstream.cancel()
        system.eventStream.unsubscribe(testActor, classOf[DeadLetter])
      }
    }

    "remove its address listener even when the stop handler throws" in {
      val stopHandlerCalled = new AtomicBoolean
      val cleanup = TestProbe()
      val outboundContext = new TestOutboundContext(addressA, addressB.address, new TestControlMessageSubject) {
        override def associationState: AssociationState = {
          if (stopHandlerCalled.get()) cleanup.ref ! Done
          super.associationState
        }
      }
      val (_, downstream) = setupStream(
        outboundContext,
        postStopAction = _ => {
          stopHandlerCalled.set(true)
          throw new IllegalStateException("stop handler failed")
        })
      try {
        downstream.request(1)
        downstream.expectNext(HandshakeReq(addressA, addressB.address))
        EventFilter[IllegalStateException](occurrences = 1).intercept {
          downstream.cancel()
          cleanup.expectMsg(Done)
        }
      } finally downstream.cancel()
    }
  }

}
