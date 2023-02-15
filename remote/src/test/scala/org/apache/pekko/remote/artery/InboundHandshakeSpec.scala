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

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Address
import pekko.remote.UniqueAddress
import pekko.remote.artery.OutboundHandshake.HandshakeReq
import pekko.remote.artery.OutboundHandshake.HandshakeRsp
import pekko.stream.scaladsl.Keep
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender
import pekko.testkit.TestProbe
import pekko.util.OptionVal

object InboundHandshakeSpec {
  case object Control1 extends ControlMessage
  case object Control2 extends ControlMessage
  case object Control3 extends ControlMessage
}

class InboundHandshakeSpec extends PekkoSpec("""
    pekko.stream.materializer.debug.fuzzing-mode = on
  """) with ImplicitSender {

  val addressA = UniqueAddress(Address("pekko", "sysA", "hostA", 1001), 1)
  val addressB = UniqueAddress(Address("pekko", "sysB", "hostB", 1002), 2)

  private def setupStream(inboundContext: InboundContext): (TestPublisher.Probe[AnyRef], TestSubscriber.Probe[Any]) = {
    val recipient = OptionVal.None // not used
    TestSource
      .probe[AnyRef]
      .map(msg =>
        InboundEnvelope(recipient, msg, OptionVal.None, addressA.uid, inboundContext.association(addressA.uid)))
      .via(new InboundHandshake(inboundContext, inControlStream = true))
      .map { case env: InboundEnvelope => env.message }
      .toMat(TestSink.probe[Any])(Keep.both)
      .run()
  }

  private def futureUniqueRemoteAddress(association: OutboundContext): Future[UniqueAddress] = {
    val p = Promise[UniqueAddress]()
    association.associationState.addUniqueRemoteAddressListener(a => p.success(a))
    p.future
  }

  "InboundHandshake stage" must {

    "send HandshakeRsp as reply to HandshakeReq" in {
      val replyProbe = TestProbe()
      val inboundContext = new TestInboundContext(addressB, controlProbe = Some(replyProbe.ref))
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      upstream.sendNext(HandshakeReq(addressA, addressB.address))
      upstream.sendNext("msg1")
      replyProbe.expectMsg(HandshakeRsp(addressB))
      downstream.expectNext("msg1")
      downstream.cancel()
    }

    "complete remoteUniqueAddress when receiving HandshakeReq" in {
      val inboundContext = new TestInboundContext(addressB)
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      upstream.sendNext(HandshakeReq(addressA, addressB.address))
      upstream.sendNext("msg1")
      downstream.expectNext("msg1")
      val uniqueRemoteAddress =
        Await.result(futureUniqueRemoteAddress(inboundContext.association(addressA.address)), remainingOrDefault)
      uniqueRemoteAddress should ===(addressA)
      downstream.cancel()
    }

    "drop message from unknown (receiving system restarted)" in {
      val replyProbe = TestProbe()
      val inboundContext = new TestInboundContext(addressB, controlProbe = Some(replyProbe.ref))
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      // no HandshakeReq
      upstream.sendNext("msg17")
      downstream.expectNoMessage(200.millis) // messages from unknown are dropped

      // and accept messages after handshake
      upstream.sendNext(HandshakeReq(addressA, addressB.address))
      upstream.sendNext("msg18")
      replyProbe.expectMsg(HandshakeRsp(addressB))
      downstream.expectNext("msg18")
      upstream.sendNext("msg19")
      downstream.expectNext("msg19")

      downstream.cancel()
    }

  }

}
