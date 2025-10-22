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

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdScalarDeserializer
import tools.jackson.databind.ext.javatime.deser.DurationDeserializer
import tools.jackson.databind.ext.javatime.ser.DurationSerializer
import tools.jackson.databind.ser.std.StdScalarSerializer

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * INTERNAL API: Adds support for serializing and deserializing [[FiniteDuration]].
 */
@InternalApi private[pekko] trait FiniteDurationModule extends JacksonModule {
  addSerializer(
    classOf[FiniteDuration],
    () => FiniteDurationSerializer.instance,
    () => FiniteDurationDeserializer.instance)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object FiniteDurationSerializer {
  val instance: FiniteDurationSerializer = new FiniteDurationSerializer
}

/**
 * INTERNAL API: Delegates to DurationSerializer in `jackson-databind`
 */
@InternalApi private[pekko] class FiniteDurationSerializer
    extends StdScalarSerializer[FiniteDuration](classOf[FiniteDuration]) {
  override def serialize(value: FiniteDuration, jgen: JsonGenerator, provider: SerializationContext): Unit = {
    DurationSerializer.INSTANCE.serialize(value.toJava, jgen, provider)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object FiniteDurationDeserializer {
  val instance: FiniteDurationDeserializer = new FiniteDurationDeserializer
}

/**
 * INTERNAL API: Delegates to DurationDeserializer in `jackson-databind`
 */
@InternalApi private[pekko] class FiniteDurationDeserializer
    extends StdScalarDeserializer[FiniteDuration](classOf[FiniteDuration]) {

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): FiniteDuration = {
    DurationDeserializer.INSTANCE.deserialize(jp, ctxt).toScala
  }
}
