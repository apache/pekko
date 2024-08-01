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

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.core.util.VersionUtil
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.Module.SetupContext
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.`type`.TypeModifier
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.fasterxml.jackson.databind.ser.Serializers

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object JacksonModule {

  lazy val version: Version = {
    val groupId = "org.apache.pekko"
    val artifactId = "serialization-jackson"
    val version = pekko.Version.current
    VersionUtil.parseVersion(version, groupId, artifactId)
  }

  class SerializerResolverByClass(clazz: Class[_], deserializer: () => JsonSerializer[_]) extends Serializers.Base {

    override def findSerializer(
        config: SerializationConfig,
        javaType: JavaType,
        beanDesc: BeanDescription): JsonSerializer[_] =
      if (clazz.isAssignableFrom(javaType.getRawClass))
        deserializer()
      else
        super.findSerializer(config, javaType, beanDesc)

  }

  class DeserializerResolverByClass(clazz: Class[_], serializer: () => JsonDeserializer[_]) extends Deserializers.Base {

    override def findBeanDeserializer(
        javaType: JavaType,
        config: DeserializationConfig,
        beanDesc: BeanDescription): JsonDeserializer[_] =
      if (clazz.isAssignableFrom(javaType.getRawClass))
        serializer()
      else
        super.findBeanDeserializer(javaType, config, beanDesc)

  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object VersionExtractor {
  def unapply(v: Version) = Some((v.getMajorVersion, v.getMinorVersion))
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] trait JacksonModule extends Module {
  import JacksonModule._

  private val initializers = Seq.newBuilder[SetupContext => Unit]

  def version: Version = JacksonModule.version

  def setupModule(context: SetupContext): Unit =
    initializers.result().foreach(_.apply(context))

  def addSerializer(
      clazz: Class[_],
      serializer: () => JsonSerializer[_],
      deserializer: () => JsonDeserializer[_]): this.type =
    this += { ctx =>
      ctx.addSerializers(new SerializerResolverByClass(clazz, serializer))
      ctx.addDeserializers(new DeserializerResolverByClass(clazz, deserializer))
    }

  protected def +=(init: SetupContext => Unit): this.type = { initializers += init; this }
  protected def +=(ser: Serializers): this.type = this += (_.addSerializers(ser))
  protected def +=(deser: Deserializers): this.type = this += (_.addDeserializers(deser))
  protected def +=(typeMod: TypeModifier): this.type = this += (_.addTypeModifier(typeMod))
  protected def +=(beanSerMod: BeanSerializerModifier): this.type = this += (_.addBeanSerializerModifier(beanSerMod))

}
