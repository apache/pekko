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

import tools.jackson.core.Version
import tools.jackson.core.util.VersionUtil
import tools.jackson.databind.BeanDescription
import tools.jackson.databind.DeserializationConfig
import tools.jackson.databind.JavaType
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.JacksonModule.SetupContext
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.`type`.TypeModifier
import tools.jackson.databind.deser.Deserializers
import tools.jackson.databind.ser.BeanSerializerModifier
import tools.jackson.databind.ser.Serializers

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

  class SerializerResolverByClass(clazz: Class[_], deserializer: () => ValueSerializer[_]) extends Serializers.Base {

    override def findSerializer(
        config: SerializationConfig,
        javaType: JavaType,
        beanDesc: BeanDescription): ValueSerializer[_] = {
      if (clazz.isAssignableFrom(javaType.getRawClass))
        deserializer()
      else
        super.findSerializer(config, javaType, beanDesc)
    }

  }

  class DeserializerResolverByClass(clazz: Class[_], serializer: () => ValueDeserializer[_]) extends Deserializers.Base {

    override def findBeanDeserializer(
        javaType: JavaType,
        config: DeserializationConfig,
        beanDesc: BeanDescription): ValueDeserializer[_] = {
      if (clazz.isAssignableFrom(javaType.getRawClass))
        serializer()
      else
        super.findBeanDeserializer(javaType, config, beanDesc)
    }

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
@InternalApi private[pekko] trait JacksonModule extends tools.jackson.databind.JacksonModule {
  import JacksonModule._

  private val initializers = Seq.newBuilder[SetupContext => Unit]

  def version: Version = JacksonModule.version

  def setupModule(context: SetupContext): Unit = {
    initializers.result().foreach(_.apply(context))
  }

  def addSerializer(
      clazz: Class[_],
      serializer: () => ValueSerializer[_],
      deserializer: () => ValueDeserializer[_]): this.type = {
    this += { ctx =>
      ctx.addSerializers(new SerializerResolverByClass(clazz, serializer))
      ctx.addDeserializers(new DeserializerResolverByClass(clazz, deserializer))
    }
  }

  protected def +=(init: SetupContext => Unit): this.type = { initializers += init; this }
  protected def +=(ser: Serializers): this.type = this += (_.addSerializers(ser))
  protected def +=(deser: Deserializers): this.type = this += (_.addDeserializers(deser))
  protected def +=(typeMod: TypeModifier): this.type = this += (_.addTypeModifier(typeMod))
  protected def +=(beanSerMod: BeanSerializerModifier): this.type = this += (_.addBeanSerializerModifier(beanSerMod))

}
