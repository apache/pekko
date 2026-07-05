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

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

import scala.Enumeration
import scala.language.existentials

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.ContextualSerializer
import com.fasterxml.jackson.databind.ser.Serializers
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

import org.apache.pekko.annotation.InternalApi

/**
 * Complete module with support for all custom serializers.
 */
class PekkoJacksonModule
    extends JacksonModule
    with ActorRefModule
    with AddressModule
    with FiniteDurationModule
    with ScalaEnumerationModule {
  override def getModuleName = "PekkoJacksonModule"
}

object PekkoJacksonModule extends PekkoJacksonModule

class PekkoTypedJacksonModule extends JacksonModule with TypedActorRefModule {
  override def getModuleName = "PekkoTypedJacksonModule"
}

object PekkoTypedJacksonModule extends PekkoJacksonModule

class PekkoStreamJacksonModule extends JacksonModule with StreamRefModule {
  override def getModuleName = "PekkoStreamJacksonModule"
}

object PekkoStreamJacksonModule extends PekkoJacksonModule

/**
 * INTERNAL API: Overrides jackson-module-scala's legacy Enumeration serializer.
 *
 * DefaultScalaModule supports scala.Enumeration, but its legacy serializer reads the enum owner from the private
 * $outer field with reflection. That fails for Scala 3.8+ compiled Enumeration values. This serializer keeps the same
 * legacy wire format and uses MethodHandles to read the enum owner.
 */
@InternalApi private[pekko] trait ScalaEnumerationModule extends JacksonModule {
  this += new Serializers.Base {
    override def findSerializer(
        config: SerializationConfig,
        javaType: JavaType,
        beanDesc: BeanDescription): JsonSerializer[?] = {
      if (classOf[Enumeration#Value].isAssignableFrom(javaType.getRawClass))
        ScalaEnumerationSerializer.instance
      else
        super.findSerializer(config, javaType, beanDesc)
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ScalaEnumerationSerializer {
  val instance: ScalaEnumerationSerializer = new ScalaEnumerationSerializer
  private val stringInstance: ScalaEnumerationSerializer = new ScalaEnumerationSerializer(asString = true)

  private val OuterFieldName = "$outer"
  private val outerGetters = new ConcurrentHashMap[Class[?], MethodHandle]

  private val createOuterGetter = new Function[Class[?], MethodHandle] {
    override def apply(valueClass: Class[?]): MethodHandle = {
      val outerField = valueClass.getDeclaredFields
        .find(_.getName == OuterFieldName)
        .getOrElse(throw new IllegalArgumentException(s"Could not find $OuterFieldName field on ${valueClass.getName}"))

      MethodHandles.privateLookupIn(valueClass, MethodHandles.lookup()).unreflectGetter(outerField)
    }
  }

  def enumClassName(value: Enumeration#Value): String = {
    val owner = outerEnumeration(value)
    owner.getClass.getName.stripSuffix("$")
  }

  private def outerEnumeration(value: Enumeration#Value): Enumeration = {
    val valueClass = value.asInstanceOf[AnyRef].getClass.getSuperclass
    val getter = outerGetters.computeIfAbsent(valueClass, createOuterGetter)
    getter.invokeWithArguments(value.asInstanceOf[AnyRef]).asInstanceOf[Enumeration]
  }

  def forProperty(property: BeanProperty): ScalaEnumerationSerializer = {
    if (property != null && property.getAnnotation(classOf[JsonScalaEnumeration]) != null)
      stringInstance
    else
      instance
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class ScalaEnumerationSerializer(asString: Boolean = false)
    extends StdScalarSerializer[Enumeration#Value](classOf[Enumeration#Value])
    with ContextualSerializer {
  import ScalaEnumerationSerializer._

  override def serialize(value: Enumeration#Value, jgen: JsonGenerator, provider: SerializerProvider): Unit = {
    if (asString)
      jgen.writeString(value.toString)
    else {
      // Keep the legacy jackson-module-scala wire format, but use MethodHandles to read the enum owner on Scala 3.8+.
      jgen.writeStartObject()
      jgen.writeStringField("enumClass", enumClassName(value))
      jgen.writeStringField("value", value.toString)
      jgen.writeEndObject()
    }
  }

  override def createContextual(provider: SerializerProvider, property: BeanProperty): JsonSerializer[?] =
    forProperty(property)
}
