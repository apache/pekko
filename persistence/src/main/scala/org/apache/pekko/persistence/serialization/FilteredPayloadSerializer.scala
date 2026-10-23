/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.serialization

import java.io.NotSerializableException

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.persistence.FilteredPayload
import pekko.persistence.SerializedEvent
import pekko.persistence.serialization.{ MessageFormats => mf }
import pekko.protobufv3.internal.ByteString
import pekko.protobufv3.internal.UnsafeByteOperations
import pekko.serialization.BaseSerializer
import pekko.serialization.SerializerWithStringManifest

/**
 * INTERNAL API
 *
 * Serializer for persistence payload wrappers: `FilteredPayload` and `SerializedEvent`.
 */
@InternalApi
final class FilteredPayloadSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {

  private val FilteredPayloadManifest = "F"
  private val SerializedEventManifest = "S"

  override def manifest(o: AnyRef): String = o match {
    case FilteredPayload    => FilteredPayloadManifest
    case _: SerializedEvent => SerializedEventManifest
    case _                  => throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass}")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case FilteredPayload     => Array.empty
    case se: SerializedEvent => payloadBuilder(se).build.toByteArray
    case _                   => throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass}")
  }

  private def payloadBuilder(se: SerializedEvent): mf.PersistentPayload.Builder = {
    val builder = mf.PersistentPayload.newBuilder()
    builder.setPayload(UnsafeByteOperations.unsafeWrap(se.bytes))
    builder.setSerializerId(se.serializerId)
    if (se.serializerManifest.nonEmpty) builder.setPayloadManifest(ByteString.copyFromUtf8(se.serializerManifest))
    builder
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case FilteredPayloadManifest => FilteredPayload
    case SerializedEventManifest => serializedEvent(mf.PersistentPayload.parseFrom(bytes))
    case _                       => throw new NotSerializableException(s"Unknown manifest [$manifest]")
  }

  private def serializedEvent(persistentPayload: mf.PersistentPayload): SerializedEvent = {
    val manifest =
      if (persistentPayload.hasPayloadManifest)
        persistentPayload.getPayloadManifest.toStringUtf8
      else ""

    new SerializedEvent(persistentPayload.getPayload.toByteArray, persistentPayload.getSerializerId, manifest)
  }

}
