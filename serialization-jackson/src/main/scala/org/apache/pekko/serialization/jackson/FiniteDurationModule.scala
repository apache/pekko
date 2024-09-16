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

import scala.concurrent.duration.FiniteDuration

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.DurationDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.DurationSerializer

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.JavaDurationConverters._

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
 * INTERNAL API: Delegates to DurationSerializer in `jackson-modules-java8`
 */
@InternalApi private[pekko] class FiniteDurationSerializer
    extends StdScalarSerializer[FiniteDuration](classOf[FiniteDuration]) {
  override def serialize(value: FiniteDuration, jgen: JsonGenerator, provider: SerializerProvider): Unit =
    DurationSerializer.INSTANCE.serialize(value.asJava, jgen, provider)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object FiniteDurationDeserializer {
  val instance: FiniteDurationDeserializer = new FiniteDurationDeserializer
}

/**
 * INTERNAL API: Delegates to DurationDeserializer in `jackson-modules-java8`
 */
@InternalApi private[pekko] class FiniteDurationDeserializer
    extends StdScalarDeserializer[FiniteDuration](classOf[FiniteDuration]) {

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): FiniteDuration =
    DurationDeserializer.INSTANCE.deserialize(jp, ctxt).asScala
}
