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

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonTokenId
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdScalarDeserializer
import tools.jackson.databind.ser.std.StdScalarSerializer

import org.apache.pekko
import pekko.actor.Address
import pekko.actor.AddressFromURIString
import pekko.annotation.InternalApi

/**
 * INTERNAL API: Adds support for serializing and deserializing [[Address]].
 */
@InternalApi private[pekko] trait AddressModule extends JacksonModule {
  addSerializer(classOf[Address], () => AddressSerializer.instance, () => AddressDeserializer.instance)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object AddressSerializer {
  val instance: AddressSerializer = new AddressSerializer
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class AddressSerializer extends StdScalarSerializer[Address](classOf[Address]) {
  override def serialize(value: Address, jgen: JsonGenerator, provider: SerializationContext): Unit = {
    jgen.writeString(value.toString)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object AddressDeserializer {
  val instance: AddressDeserializer = new AddressDeserializer
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class AddressDeserializer extends StdScalarDeserializer[Address](classOf[Address]) {

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): Address = {
    if (jp.currentTokenId() == JsonTokenId.ID_STRING) {
      val serializedAddress = jp.getString()
      AddressFromURIString(serializedAddress)
    } else
      ctxt.handleUnexpectedToken(handledType(), jp).asInstanceOf[Address]
  }
}
