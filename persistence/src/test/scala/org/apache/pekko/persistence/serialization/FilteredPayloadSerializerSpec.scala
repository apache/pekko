/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.serialization

import org.apache.pekko
import pekko.persistence.FilteredPayload
import pekko.persistence.SerializedEvent
import pekko.serialization.SerializationExtension
import pekko.serialization.SerializerWithStringManifest
import pekko.serialization.Serializers
import pekko.testkit.PekkoSpec

class FilteredPayloadSerializerSpec extends PekkoSpec {

  private val serialization = SerializationExtension(system)

  "FilteredPayload serializer" should {
    "serialize FilteredPayload to zero-byte array" in {
      val serializer = serialization.findSerializerFor(FilteredPayload).asInstanceOf[SerializerWithStringManifest]
      val manifest = serializer.manifest(FilteredPayload)
      val serialized = serializer.toBinary(FilteredPayload)
      serialized should have(size(0))
      serializer.fromBinary(serialized, manifest) should be(FilteredPayload)
    }

    "keep the existing serializer identifier and manifest for FilteredPayload" in {
      val serializer = serialization.findSerializerFor(FilteredPayload)
      serializer.identifier shouldBe 34
      Serializers.manifestFor(serializer, FilteredPayload) shouldBe "F"
    }

    "serialize SerializedEvent" in {
      val event = "event1"
      val eventSerializer = serialization.findSerializerFor(event)
      val serializedEvent = new SerializedEvent(
        bytes = eventSerializer.toBinary(event),
        serializerId = eventSerializer.identifier,
        serializerManifest = Serializers.manifestFor(eventSerializer, event))

      val serializedEventSerializer = serialization.findSerializerFor(serializedEvent)
      serializedEventSerializer.identifier shouldBe 34
      val serializedEventBytes = serialization.serialize(serializedEvent).get

      val deserialized = serialization
        .deserialize(
          serializedEventBytes,
          serializedEventSerializer.identifier,
          Serializers.manifestFor(serializedEventSerializer, serializedEvent))
        .get
        .asInstanceOf[SerializedEvent]

      deserialized.serializerId shouldBe eventSerializer.identifier
      deserialized.serializerManifest shouldBe serializedEvent.serializerManifest
      val deserializedEvent =
        serialization.deserialize(deserialized.bytes, deserialized.serializerId, deserialized.serializerManifest).get
      deserializedEvent shouldBe event
    }

    "serialize SerializedEvent with a non-empty serializer manifest" in {
      val serializedEvent = new SerializedEvent(Array[Byte](1, 2, 3), 4711, "my-manifest")
      val serializer = serialization.findSerializerFor(serializedEvent).asInstanceOf[SerializerWithStringManifest]
      val deserialized = serializer
        .fromBinary(serializer.toBinary(serializedEvent), serializer.manifest(serializedEvent))
        .asInstanceOf[SerializedEvent]
      deserialized.bytes.toSeq shouldBe Seq[Byte](1, 2, 3)
      deserialized.serializerId shouldBe 4711
      deserialized.serializerManifest shouldBe "my-manifest"
    }
  }

}
