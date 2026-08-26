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

package org.apache.pekko.remote

import java.lang.reflect.Method

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Guards the pekko-remote internals that the OpenTelemetry Java agent attaches bytecode advice to.
 *
 * The agent matches these by name and signature, and its muzzle checks do not verify the matchers. When a
 * match stops applying the instrumentation is silently disabled, so this spec fails loudly instead. Keep it
 * in sync with https://github.com/apache/pekko/issues/3472
 */
class InstrumentationPointsSpec extends AnyWordSpec with Matchers {

  private def declaredMethods(className: String): Array[Method] =
    Class.forName(className, false, getClass.getClassLoader).getDeclaredMethods

  private def paramTypeNames(m: Method): Seq[String] = m.getParameterTypes.toIndexedSeq.map(_.getName)

  "The artery members instrumented by the OpenTelemetry Java agent" should {

    "include RemoteInstruments.create returning a Vector" in {
      declaredMethods("org.apache.pekko.remote.artery.RemoteInstruments$").filter(_.getName == "create").exists(
        _.getReturnType.getName == "scala.collection.immutable.Vector") shouldBe true
    }

    "include RemoteInstruments.serialize taking a ByteBuffer as second argument" in {
      declaredMethods("org.apache.pekko.remote.artery.RemoteInstruments").filter(_.getName == "serialize").exists(
        _.getParameterTypes.lift(1).exists(_.getName == "java.nio.ByteBuffer")) shouldBe true
    }

    "include RemoteInstruments.deserialize taking an InboundEnvelope" in {
      declaredMethods("org.apache.pekko.remote.artery.RemoteInstruments").filter(_.getName == "deserialize").exists(
        paramTypeNames(_) == Seq("org.apache.pekko.remote.artery.InboundEnvelope")) shouldBe true
    }

    "include the three argument ReusableOutboundEnvelope.init, and no arg copy and clear" in {
      val methods = declaredMethods("org.apache.pekko.remote.artery.ReusableOutboundEnvelope")
      methods.filter(_.getName == "init").exists(_.getParameterCount == 3) shouldBe true
      methods.filter(_.getName == "copy").exists(_.getParameterCount == 0) shouldBe true
      methods.filter(_.getName == "clear").exists(_.getParameterCount == 0) shouldBe true
    }

    "include the nine argument ReusableInboundEnvelope.init, and no arg clear" in {
      val methods = declaredMethods("org.apache.pekko.remote.artery.ReusableInboundEnvelope")
      methods.filter(_.getName == "init").exists(_.getParameterCount == 9) shouldBe true
      methods.filter(_.getName == "clear").exists(_.getParameterCount == 0) shouldBe true
    }

    "include artery MessageDispatcher.dispatch taking an InboundEnvelope" in {
      declaredMethods("org.apache.pekko.remote.artery.MessageDispatcher").filter(_.getName == "dispatch").exists(
        paramTypeNames(_) == Seq("org.apache.pekko.remote.artery.InboundEnvelope")) shouldBe true
    }
  }

  "The classic remoting members instrumented by the OpenTelemetry Java agent" should {

    "include the four argument EndpointManager.Send constructor and copy" in {
      val send = Class.forName("org.apache.pekko.remote.EndpointManager$Send", false, getClass.getClassLoader)
      send.getDeclaredConstructors.exists(_.getParameterCount == 4) shouldBe true
      send.getDeclaredMethods.filter(_.getName == "copy").exists(_.getParameterCount == 4) shouldBe true
    }

    "include EndpointWriter.writeSend taking an EndpointManager.Send" in {
      declaredMethods("org.apache.pekko.remote.EndpointWriter").filter(_.getName == "writeSend").exists(
        paramTypeNames(_) == Seq("org.apache.pekko.remote.EndpointManager$Send")) shouldBe true
    }

    "include DefaultMessageDispatcher.dispatch taking a SerializedMessage as third argument" in {
      declaredMethods("org.apache.pekko.remote.DefaultMessageDispatcher").filter(_.getName == "dispatch").exists(
        _.getParameterTypes.lift(2).exists(
          _.getName == "org.apache.pekko.remote.WireFormats$SerializedMessage")) shouldBe true
    }

    "include PekkoPduProtobufCodec.constructMessage and decodeMessage" in {
      val codec = declaredMethods("org.apache.pekko.remote.transport.PekkoPduProtobufCodec$")
      codec.exists(_.getName == "constructMessage") shouldBe true
      codec.filter(_.getName == "decodeMessage").exists(
        _.getParameterTypes.headOption.exists(_.getName == "org.apache.pekko.util.ByteString")) shouldBe true
    }
  }
}
