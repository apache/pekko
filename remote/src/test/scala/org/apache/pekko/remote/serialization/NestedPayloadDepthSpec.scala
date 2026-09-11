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

package org.apache.pekko.remote.serialization

import java.io.NotSerializableException

import org.apache.pekko
import pekko.actor.Status
import pekko.protobufv3.internal.{ ByteString => ProtoByteString }
import pekko.remote.ContainerFormats
import pekko.serialization.SerializationExtension
import pekko.serialization.Serializers
import pekko.testkit.PekkoSpec

class NestedPayloadDepthSpec extends PekkoSpec("""
    pekko.actor.allow-java-serialization = off
  """) {

  private val serialization = SerializationExtension(system)
  private val MiscSerializerId = 16
  private val OptionManifest = "C"

  private def payloadFor(obj: AnyRef): ContainerFormats.Payload = {
    val ser = serialization.findSerializerFor(obj)
    ContainerFormats.Payload
      .newBuilder()
      .setEnclosedMessage(ProtoByteString.copyFrom(serialization.serialize(obj).get))
      .setSerializerId(ser.identifier)
      .setMessageManifest(ProtoByteString.copyFromUtf8(Serializers.manifestFor(ser, obj)))
      .build()
  }

  /** Wraps a payload in one more `Some(...)` layer, as the wire format encodes it. */
  private def wrapInOption(inner: ContainerFormats.Payload): ContainerFormats.Payload = {
    val optionBytes = ContainerFormats.Option.newBuilder().setValue(inner).build().toByteArray
    ContainerFormats.Payload
      .newBuilder()
      .setEnclosedMessage(ProtoByteString.copyFrom(optionBytes))
      .setSerializerId(MiscSerializerId)
      .setMessageManifest(ProtoByteString.copyFromUtf8(OptionManifest))
      .build()
  }

  private def nestedOption(depth: Int): ContainerFormats.Payload = (1 to depth).foldLeft(payloadFor(pekko.Done))(
    (acc, _) => wrapInOption(acc))

  private def deserialize(p: ContainerFormats.Payload): AnyRef =
    serialization.deserialize(p.getEnclosedMessage.toByteArray, p.getSerializerId, OptionManifest).get

  "Deserialization of a nested payload" must {

    "accept nesting within the configured depth" in {
      serialization.maxNestingDepth should ===(32)
      deserialize(nestedOption(4)) should ===(Some(Some(Some(Some(pekko.Done)))))
    }

    "reject nesting beyond the configured depth with NotSerializableException" in {
      // Deeper than the limit, but still a tiny message: without a bound the depth of
      // this recursion is limited only by the stack.
      val ex = intercept[NotSerializableException] {
        deserialize(nestedOption(200))
      }
      ex.getMessage should include("nesting depth")
    }

    "reject deep nesting that would otherwise exhaust the stack" in {
      val deep = nestedOption(20000)
      withClue(s"payload is only ${deep.getEnclosedMessage.size()} bytes: ") {
        intercept[NotSerializableException] {
          deserialize(deep)
        }
      }
    }

    "not leak depth between messages" in {
      intercept[NotSerializableException](deserialize(nestedOption(200)))
      // a later, well-formed message must still be accepted
      deserialize(nestedOption(4)) should ===(Some(Some(Some(Some(pekko.Done)))))
    }

    "still deserialize ordinary wrapped messages" in {
      val failure = Status.Failure(new IllegalArgumentException("boom"))
      val roundTripped = serialization
        .deserialize(
          serialization.serialize(failure).get,
          serialization.findSerializerFor(failure).identifier,
          Serializers.manifestFor(serialization.findSerializerFor(failure), failure))
        .get
      roundTripped.getClass should ===(classOf[Status.Failure])
    }
  }
}
