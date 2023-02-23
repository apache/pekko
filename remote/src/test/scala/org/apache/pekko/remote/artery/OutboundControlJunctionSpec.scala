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

import org.apache.pekko
import pekko.actor.Address
import pekko.remote.UniqueAddress
import pekko.stream.scaladsl.Keep
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender
import pekko.util.OptionVal

object OutboundControlJunctionSpec {
  case object Control1 extends ControlMessage
  case object Control2 extends ControlMessage
  case object Control3 extends ControlMessage
}

class OutboundControlJunctionSpec extends PekkoSpec("""
    pekko.stream.materializer.debug.fuzzing-mode = on
  """) with ImplicitSender {
  import OutboundControlJunctionSpec._

  val addressA = UniqueAddress(Address("pekko", "sysA", "hostA", 1001), 1)
  val addressB = UniqueAddress(Address("pekko", "sysB", "hostB", 1002), 2)

  private val outboundEnvelopePool = ReusableOutboundEnvelope.createObjectPool(capacity = 16)

  "Control messages" must {

    "be injected via side channel" in {
      val inboundContext = new TestInboundContext(localAddress = addressA)
      val outboundContext = inboundContext.association(addressB.address)

      val ((upstream, controlIngress), downstream) = TestSource
        .probe[String]
        .map(msg => outboundEnvelopePool.acquire().init(OptionVal.None, msg, OptionVal.None))
        .viaMat(new OutboundControlJunction(outboundContext, outboundEnvelopePool))(Keep.both)
        .map(env => env.message)
        .toMat(TestSink.probe[Any])(Keep.both)
        .run()

      controlIngress.sendControlMessage(Control1)
      downstream.request(1)
      downstream.expectNext(Control1)
      upstream.sendNext("msg1")
      downstream.request(1)
      downstream.expectNext("msg1")
      upstream.sendNext("msg2")
      downstream.request(1)
      downstream.expectNext("msg2")
      controlIngress.sendControlMessage(Control2)
      upstream.sendNext("msg3")
      downstream.request(10)
      downstream.expectNextUnorderedN(List("msg3", Control2))
      controlIngress.sendControlMessage(Control3)
      downstream.expectNext(Control3)
      downstream.cancel()
    }

  }

}
