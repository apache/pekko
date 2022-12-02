/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.Address
import pekko.actor.ExtendedActorSystem
import pekko.remote.UniqueAddress
import pekko.remote.artery.OutboundHandshake.HandshakeReq
import pekko.serialization.SerializationExtension
import pekko.serialization.SerializerWithStringManifest
import pekko.stream.scaladsl.Keep
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender
import pekko.util.OptionVal

class DuplicateHandshakeSpec extends PekkoSpec("""
      pekko.stream.materializer.debug.fuzzing-mode = on
  """) with ImplicitSender {

  val pool = new EnvelopeBufferPool(1034 * 1024, 128)
  val serialization = SerializationExtension(system)

  val addressA = UniqueAddress(Address("akka", "sysA", "hostA", 1001), 1)
  val addressB = UniqueAddress(Address("akka", "sysB", "hostB", 1002), 2)

  private def setupStream(inboundContext: InboundContext): (TestPublisher.Probe[AnyRef], TestSubscriber.Probe[Any]) = {
    TestSource
      .probe[AnyRef]
      .map { msg =>
        val association = inboundContext.association(addressA.uid)
        val ser = serialization.serializerFor(msg.getClass)
        val serializerId = ser.identifier
        val manifest = ser match {
          case s: SerializerWithStringManifest => s.manifest(msg)
          case _                               => ""
        }

        val env = new ReusableInboundEnvelope
        env
          .init(
            recipient = OptionVal.None,
            sender = OptionVal.None,
            originUid = addressA.uid,
            serializerId,
            manifest,
            flags = 0,
            envelopeBuffer = null,
            association,
            lane = 0)
          .withMessage(msg)
        env
      }
      .via(new DuplicateHandshakeReq(numberOfLanes = 3, inboundContext, system.asInstanceOf[ExtendedActorSystem], pool))
      .map { case env: InboundEnvelope => env.message -> env.lane }
      .toMat(TestSink.probe[Any])(Keep.both)
      .run()
  }

  "DuplicateHandshake stage" must {

    "duplicate initial HandshakeReq" in {
      val inboundContext = new TestInboundContext(addressB, controlProbe = None)
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      val req = HandshakeReq(addressA, addressB.address)
      upstream.sendNext(req)
      upstream.sendNext("msg1")
      downstream.expectNext((req, 0))
      downstream.expectNext((req, 1))
      downstream.expectNext((req, 2))
      downstream.expectNext(("msg1", 0))
      upstream.sendNext(req)
      downstream.expectNext((req, 0))
      downstream.expectNext((req, 1))
      downstream.expectNext((req, 2))
      downstream.cancel()
    }

    "not duplicate after handshake completed" in {
      val inboundContext = new TestInboundContext(addressB, controlProbe = None)
      val (upstream, downstream) = setupStream(inboundContext)

      downstream.request(10)
      val req = HandshakeReq(addressA, addressB.address)
      upstream.sendNext(req)
      downstream.expectNext((req, 0))
      downstream.expectNext((req, 1))
      downstream.expectNext((req, 2))
      upstream.sendNext("msg1")
      downstream.expectNext(("msg1", 0))

      inboundContext.completeHandshake(addressA)
      upstream.sendNext("msg2")
      downstream.expectNext(("msg2", 0))
      upstream.sendNext(req)
      downstream.expectNext((req, 0))
      upstream.sendNext("msg3")
      downstream.expectNext(("msg3", 0))
      downstream.cancel()
    }

  }

}
