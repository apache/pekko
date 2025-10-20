/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization.jackson3

import tools.jackson.core.{ JsonGenerator, JsonParser }
import tools.jackson.databind.{ DeserializationContext, JsonNode, SerializationContext }
import tools.jackson.databind.deser.std.StdScalarDeserializer
import tools.jackson.databind.ser.std.StdScalarSerializer

import org.apache.pekko.serialization.{ SerializationExtension, Serializer, Serializers }

final class PekkoSerializationSerializer extends StdScalarSerializer[AnyRef](classOf[AnyRef]) with ActorSystemAccess {
  def serialization = SerializationExtension(currentSystem())
  override def serialize(value: AnyRef, jgen: JsonGenerator, provider: SerializationContext): Unit = {
    val serializer: Serializer = serialization.findSerializerFor(value)
    val serId = serializer.identifier
    val manifest = Serializers.manifestFor(serializer, value)
    val serialized = serializer.toBinary(value)
    jgen.writeStartObject()
    jgen.writeName("serId")
    jgen.writeString(serId.toString)
    jgen.writeName("serManifest")
    jgen.writeString(manifest)
    jgen.writeName("payload")
    jgen.writeBinary(serialized)
    jgen.writeEndObject()
  }
}

final class PekkoSerializationDeserializer
    extends StdScalarDeserializer[AnyRef](classOf[AnyRef])
    with ActorSystemAccess {

  def serialization = SerializationExtension(currentSystem())

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): AnyRef = {
    val jsonNode = jp.readValueAsTree[JsonNode]()
    val id = jsonNode.get("serId").stringValue().toInt
    val manifest = jsonNode.get("serManifest").stringValue()
    val payload = jsonNode.get("payload").binaryValue()
    serialization.deserialize(payload, id, manifest).get
  }
}
