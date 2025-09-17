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

import com.fasterxml.jackson.core.{ JsonGenerator, JsonParser, JsonTokenId }
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer
import com.fasterxml.jackson.databind.{ DeserializationContext, SerializerProvider }

import org.apache.pekko
import pekko.actor.typed.scaladsl.adapter._
import pekko.actor.typed.{ ActorRef, ActorRefResolver }
import pekko.annotation.InternalApi

/**
 * INTERNAL API: Adds support for serializing and deserializing [[pekko.actor.typed.ActorRef]].
 */
@InternalApi private[pekko] trait TypedActorRefModule extends JacksonModule {
  addSerializer(classOf[ActorRef[_]], () => TypedActorRefSerializer.instance, () => TypedActorRefDeserializer.instance)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object TypedActorRefSerializer {
  val instance: TypedActorRefSerializer = new TypedActorRefSerializer
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class TypedActorRefSerializer
    extends StdScalarSerializer[ActorRef[_]](classOf[ActorRef[_]])
    with ActorSystemAccess {
  override def serialize(value: ActorRef[_], jgen: JsonGenerator, provider: SerializerProvider): Unit = {
    val serializedActorRef = ActorRefResolver(currentSystem().toTyped).toSerializationFormat(value)
    jgen.writeString(serializedActorRef)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object TypedActorRefDeserializer {
  val instance: TypedActorRefDeserializer = new TypedActorRefDeserializer
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class TypedActorRefDeserializer
    extends StdScalarDeserializer[ActorRef[_]](classOf[ActorRef[_]])
    with ActorSystemAccess {

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): ActorRef[_] = {
    if (jp.currentTokenId() == JsonTokenId.ID_STRING) {
      val serializedActorRef = jp.getText()
      ActorRefResolver(currentSystem().toTyped).resolveActorRef(serializedActorRef)
    } else
      ctxt.handleUnexpectedToken(handledType(), jp).asInstanceOf[ActorRef[_]]
  }
}
