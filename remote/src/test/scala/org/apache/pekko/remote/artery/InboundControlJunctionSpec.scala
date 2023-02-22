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

import scala.util.Try

import org.apache.pekko
import pekko.Done
import pekko.actor.Address
import pekko.remote.UniqueAddress
import pekko.remote.artery.InboundControlJunction.ControlMessageObserver
import pekko.stream.scaladsl.Keep
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender
import pekko.testkit.TestProbe
import pekko.util.OptionVal

object InboundControlJunctionSpec {
  trait TestControlMessage extends ControlMessage

  case object Control1 extends TestControlMessage
  case object Control2 extends TestControlMessage
  case object Control3 extends TestControlMessage
}

class InboundControlJunctionSpec
    extends PekkoSpec("""
                   pekko.actor.serialization-bindings {
                     "org.apache.pekko.remote.artery.InboundControlJunctionSpec$TestControlMessage" = java
                   }
                   pekko.stream.materializer.debug.fuzzing-mode = on
                   """)
    with ImplicitSender {
  import InboundControlJunctionSpec._

  val addressA = UniqueAddress(Address("pekko", "sysA", "hostA", 1001), 1)
  val addressB = UniqueAddress(Address("pekko", "sysB", "hostB", 1002), 2)

  "Control messages" must {

    "be emitted via side channel" in {
      val observerProbe = TestProbe()
      val recipient = OptionVal.None // not used

      val ((upstream, controlSubject), downstream) = TestSource
        .probe[AnyRef]
        .map(msg => InboundEnvelope(recipient, msg, OptionVal.None, addressA.uid, OptionVal.None))
        .viaMat(new InboundControlJunction)(Keep.both)
        .map { case env: InboundEnvelope => env.message }
        .toMat(TestSink.probe[Any])(Keep.both)
        .run()

      controlSubject.attach(new ControlMessageObserver {
        override def notify(env: InboundEnvelope) = {
          observerProbe.ref ! env.message
        }
        override def controlSubjectCompleted(signal: Try[Done]): Unit = ()
      })

      downstream.request(10)
      upstream.sendNext("msg1")
      downstream.expectNext("msg1")
      upstream.sendNext(Control1)
      upstream.sendNext(Control2)
      observerProbe.expectMsg(Control1)
      observerProbe.expectMsg(Control2)
      upstream.sendNext("msg2")
      downstream.expectNext("msg2")
      upstream.sendNext(Control3)
      observerProbe.expectMsg(Control3)
      downstream.cancel()
    }

  }

}
