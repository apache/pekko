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

import org.apache.pekko
import pekko.actor.Address
import pekko.remote.UniqueAddress
import pekko.remote.artery.SystemMessageDelivery._
import pekko.stream.scaladsl.Keep
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender
import pekko.testkit.TestProbe
import pekko.util.OptionVal

class SystemMessageAckerSpec extends PekkoSpec("""
    pekko.stream.materializer.debug.fuzzing-mode = on
  """) with ImplicitSender {

  val addressA = UniqueAddress(Address("pekko", "sysA", "hostA", 1001), 1)
  val addressB = UniqueAddress(Address("pekko", "sysB", "hostB", 1002), 2)
  val addressC = UniqueAddress(Address("pekko", "sysC", "hostB", 1003), 3)

  private def setupStream(inboundContext: InboundContext): (TestPublisher.Probe[AnyRef], TestSubscriber.Probe[Any]) = {
    val recipient = OptionVal.None // not used
    TestSource
      .probe[AnyRef]
      .map {
        case sysMsg @ SystemMessageEnvelope(_, _, ackReplyTo) =>
          InboundEnvelope(recipient, sysMsg, OptionVal.None, ackReplyTo.uid, inboundContext.association(ackReplyTo.uid))
        case _ => throw new RuntimeException()
      }
      .via(new SystemMessageAcker(inboundContext))
      .map { case env: InboundEnvelope => env.message }
      .toMat(TestSink.probe[Any])(Keep.both)
      .run()
  }

  "SystemMessageAcker stage" must {

    "send Ack for expected message" in {
      val replyProbe = TestProbe()
      val inboundContext = new TestInboundContext(addressA, controlProbe = Some(replyProbe.ref))
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      upstream.sendNext(SystemMessageEnvelope("b1", 1, addressB))
      replyProbe.expectMsg(Ack(1, addressA))
      upstream.sendNext(SystemMessageEnvelope("b2", 2, addressB))
      replyProbe.expectMsg(Ack(2, addressA))
      downstream.cancel()
    }

    "send Ack for duplicate message" in {
      val replyProbe = TestProbe()
      val inboundContext = new TestInboundContext(addressA, controlProbe = Some(replyProbe.ref))
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      upstream.sendNext(SystemMessageEnvelope("b1", 1, addressB))
      replyProbe.expectMsg(Ack(1, addressA))
      upstream.sendNext(SystemMessageEnvelope("b2", 2, addressB))
      replyProbe.expectMsg(Ack(2, addressA))
      upstream.sendNext(SystemMessageEnvelope("b1", 1, addressB))
      replyProbe.expectMsg(Ack(2, addressA))
      downstream.cancel()
    }

    "send Nack for unexpected message" in {
      val replyProbe = TestProbe()
      val inboundContext = new TestInboundContext(addressA, controlProbe = Some(replyProbe.ref))
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      upstream.sendNext(SystemMessageEnvelope("b1", 1, addressB))
      replyProbe.expectMsg(Ack(1, addressA))
      upstream.sendNext(SystemMessageEnvelope("b3", 3, addressB))
      replyProbe.expectMsg(Nack(1, addressA))
      downstream.cancel()
    }

    "send Nack for unexpected first message" in {
      val replyProbe = TestProbe()
      val inboundContext = new TestInboundContext(addressA, controlProbe = Some(replyProbe.ref))
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      upstream.sendNext(SystemMessageEnvelope("b2", 2, addressB))
      replyProbe.expectMsg(Nack(0, addressA))
      downstream.cancel()
    }

    "keep track of sequence numbers per sending system" in {
      val replyProbe = TestProbe()
      val inboundContext = new TestInboundContext(addressA, controlProbe = Some(replyProbe.ref))
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      upstream.sendNext(SystemMessageEnvelope("b1", 1, addressB))
      replyProbe.expectMsg(Ack(1, addressA))
      upstream.sendNext(SystemMessageEnvelope("b2", 2, addressB))
      replyProbe.expectMsg(Ack(2, addressA))

      upstream.sendNext(SystemMessageEnvelope("c1", 1, addressC))
      replyProbe.expectMsg(Ack(1, addressA))
      upstream.sendNext(SystemMessageEnvelope("c3", 3, addressC))
      replyProbe.expectMsg(Nack(1, addressA))
      upstream.sendNext(SystemMessageEnvelope("c2", 2, addressC))
      replyProbe.expectMsg(Ack(2, addressA))
      upstream.sendNext(SystemMessageEnvelope("c3", 3, addressC))
      replyProbe.expectMsg(Ack(3, addressA))
      upstream.sendNext(SystemMessageEnvelope("c4", 4, addressC))
      replyProbe.expectMsg(Ack(4, addressA))

      upstream.sendNext(SystemMessageEnvelope("b4", 4, addressB))
      replyProbe.expectMsg(Nack(2, addressA))
      upstream.sendNext(SystemMessageEnvelope("b3", 3, addressB))
      replyProbe.expectMsg(Ack(3, addressA))

      downstream.cancel()
    }

  }

}
