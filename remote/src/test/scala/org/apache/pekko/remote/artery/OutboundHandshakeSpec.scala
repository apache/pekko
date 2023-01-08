/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Address
import pekko.remote.UniqueAddress
import pekko.remote.artery.OutboundHandshake.HandshakeReq
import pekko.remote.artery.OutboundHandshake.HandshakeTimeoutException
import pekko.stream.scaladsl.Keep
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender
import pekko.util.OptionVal

class OutboundHandshakeSpec extends PekkoSpec("""
    pekko.stream.materializer.debug.fuzzing-mode = on
  """) with ImplicitSender {

  val addressA = UniqueAddress(Address("akka", "sysA", "hostA", 1001), 1)
  val addressB = UniqueAddress(Address("akka", "sysB", "hostB", 1002), 2)

  private val outboundEnvelopePool = ReusableOutboundEnvelope.createObjectPool(capacity = 16)

  private def setupStream(
      outboundContext: OutboundContext,
      timeout: FiniteDuration = 5.seconds,
      retryInterval: FiniteDuration = 10.seconds,
      injectHandshakeInterval: FiniteDuration = 10.seconds,
      livenessProbeInterval: Duration = Duration.Undefined)
      : (TestPublisher.Probe[String], TestSubscriber.Probe[Any]) = {

    TestSource
      .probe[String]
      .map(msg => outboundEnvelopePool.acquire().init(OptionVal.None, msg, OptionVal.None))
      .via(
        new OutboundHandshake(
          system,
          outboundContext,
          outboundEnvelopePool,
          timeout,
          retryInterval,
          injectHandshakeInterval,
          livenessProbeInterval))
      .map(env => env.message)
      .toMat(TestSink.probe[Any])(Keep.both)
      .run()
  }

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
