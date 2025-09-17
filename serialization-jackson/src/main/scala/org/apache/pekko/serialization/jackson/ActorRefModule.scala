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

package org.apache.pekko.serialization.jackson

// FIXME maybe move many things to `org.apache.pekko.serialization.jackson.internal` package?

import com.fasterxml.jackson.core.{ JsonGenerator, JsonParser, JsonTokenId }
import com.fasterxml.jackson.databind.{ DeserializationContext, SerializerProvider }
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi

/**
 * INTERNAL API: Adds support for serializing and deserializing [[ActorRef]].
 */
@InternalApi private[pekko] trait ActorRefModule extends JacksonModule {
  addSerializer(classOf[ActorRef], () => ActorRefSerializer.instance, () => ActorRefDeserializer.instance)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ActorRefSerializer {
  val instance: ActorRefSerializer = new ActorRefSerializer
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class ActorRefSerializer
    extends StdScalarSerializer[ActorRef](classOf[ActorRef])
    with ActorSystemAccess {
  override def serialize(value: ActorRef, jgen: JsonGenerator, provider: SerializerProvider): Unit = {
    val serializedActorRef = value.path.toSerializationFormatWithAddress(currentSystem().provider.getDefaultAddress)
    jgen.writeString(serializedActorRef)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ActorRefDeserializer {
  val instance: ActorRefDeserializer = new ActorRefDeserializer
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class ActorRefDeserializer
    extends StdScalarDeserializer[ActorRef](classOf[ActorRef])
    with ActorSystemAccess {

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): ActorRef = {
    if (jp.currentTokenId() == JsonTokenId.ID_STRING) {
      val serializedActorRef = jp.getText()
      currentSystem().provider.resolveActorRef(serializedActorRef)
    } else
      ctxt.handleUnexpectedToken(handledType(), jp).asInstanceOf[ActorRef]
  }
}
