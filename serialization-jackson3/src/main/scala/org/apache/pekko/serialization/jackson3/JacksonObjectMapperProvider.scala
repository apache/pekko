/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization.jackson3

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.nowarn
import scala.collection.immutable
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{ Failure, Success }

import com.fasterxml.jackson.annotation.{ JsonAutoDetect, PropertyAccessor }
import com.typesafe.config.Config
import tools.jackson.core.{ StreamReadConstraints, StreamReadFeature, StreamWriteConstraints, StreamWriteFeature }
import tools.jackson.core.json.{ JsonFactory, JsonReadFeature, JsonWriteFeature }
import tools.jackson.core.util.{ BufferRecycler, JsonRecyclerPools, RecyclerPool }
import tools.jackson.databind.{
  DeserializationFeature,
  JacksonModule,
  MapperFeature,
  ObjectMapper,
  SerializationFeature
}
import tools.jackson.databind.cfg.{ DateTimeFeature, EnumFeature, MapperBuilder }
import tools.jackson.databind.introspect.VisibilityChecker
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.cbor.{ CBORFactory, CBORMapper }
import org.apache.pekko
import pekko.actor.{
  ActorSystem,
  ClassicActorSystemProvider,
  DynamicAccess,
  ExtendedActorSystem,
  Extension,
  ExtensionId,
  ExtensionIdProvider
}
import pekko.actor.setup.Setup
import pekko.annotation.InternalStableApi
import pekko.event.{ Logging, LoggingAdapter }

object JacksonObjectMapperProvider extends ExtensionId[JacksonObjectMapperProvider] with ExtensionIdProvider {
  override def get(system: ActorSystem): JacksonObjectMapperProvider = super.get(system)
  override def get(system: ClassicActorSystemProvider): JacksonObjectMapperProvider = super.get(system)

  override def lookup = JacksonObjectMapperProvider

  override def createExtension(system: ExtendedActorSystem): JacksonObjectMapperProvider =
    new JacksonObjectMapperProvider(system)

  /**
   * The configuration for a given `bindingName`.
   */
  def configForBinding(bindingName: String, systemConfig: Config): Config = {
    val basePath = "pekko.serialization.jackson3"
    val baseConf = systemConfig.getConfig(basePath)
    if (systemConfig.hasPath(s"$basePath.$bindingName"))
      systemConfig.getConfig(s"$basePath.$bindingName").withFallback(baseConf)
    else
      baseConf
  }

  private[pekko] def createJsonFactory(
      bindingName: String,
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config,
      baseJsonFactory: Option[JsonFactory]): JsonFactory = {

    val streamReadConstraints = StreamReadConstraints.builder()
      .maxNestingDepth(config.getInt("read.max-nesting-depth"))
      .maxNumberLength(config.getInt("read.max-number-length"))
      .maxStringLength(config.getInt("read.max-string-length"))
      .maxNameLength(config.getInt("read.max-name-length"))
      .maxDocumentLength(config.getLong("read.max-document-length"))
      .build()

    val streamWriteConstraints = StreamWriteConstraints.builder()
      .maxNestingDepth(config.getInt("write.max-nesting-depth"))
      .build()

    val factoryBuilder = baseJsonFactory match {
      case Some(factory) =>
        factory.rebuild()
      case None =>
        JsonFactory.builder()
    }

    val configuredStreamReadFeatures =
      features(config, "stream-read-features").map {
        case (enumName, value) => StreamReadFeature.valueOf(enumName) -> value
      }
    val streamReadFeatures =
      objectMapperFactory.overrideConfiguredStreamReadFeatures(bindingName, configuredStreamReadFeatures)
    streamReadFeatures.foreach {
      case (feature, value) => factoryBuilder.configure(feature, value)
    }

    val configuredStreamWriteFeatures =
      features(config, "stream-write-features").map {
        case (enumName, value) => StreamWriteFeature.valueOf(enumName) -> value
      }
    val streamWriteFeatures =
      objectMapperFactory.overrideConfiguredStreamWriteFeatures(bindingName, configuredStreamWriteFeatures)
    streamWriteFeatures.foreach {
      case (feature, value) => factoryBuilder.configure(feature, value)
    }

    val configuredJsonReadFeatures =
      features(config, "json-read-features").map {
        case (enumName, value) => JsonReadFeature.valueOf(enumName) -> value
      }
    val jsonReadFeatures =
      objectMapperFactory.overrideConfiguredJsonReadFeatures(bindingName, configuredJsonReadFeatures)
    jsonReadFeatures.foreach {
      case (feature, value) => factoryBuilder.configure(feature, value)
    }

    val configuredJsonWriteFeatures =
      features(config, "json-write-features").map {
        case (enumName, value) => JsonWriteFeature.valueOf(enumName) -> value
      }
    val jsonWriteFeatures =
      objectMapperFactory.overrideConfiguredJsonWriteFeatures(bindingName, configuredJsonWriteFeatures)
    jsonWriteFeatures.foreach {
      case (feature, value) => factoryBuilder.configure(feature, value)
    }

    factoryBuilder
      .streamReadConstraints(streamReadConstraints)
      .streamWriteConstraints(streamWriteConstraints)
      .recyclerPool(getBufferRecyclerPool(config))
      .build()
  }

  private def createCBORFactory(
      bindingName: String,
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config,
      baseFactory: Option[CBORFactory]): CBORFactory = {

    val streamReadConstraints = StreamReadConstraints.builder()
      .maxNestingDepth(config.getInt("read.max-nesting-depth"))
      .maxNumberLength(config.getInt("read.max-number-length"))
      .maxStringLength(config.getInt("read.max-string-length"))
      .maxNameLength(config.getInt("read.max-name-length"))
      .maxDocumentLength(config.getLong("read.max-document-length"))
      .build()

    val streamWriteConstraints = StreamWriteConstraints.builder()
      .maxNestingDepth(config.getInt("write.max-nesting-depth"))
      .build()

    val factoryBuilder = baseFactory match {
      case Some(factory) =>
        factory.rebuild()
      case None =>
        CBORFactory.builder()
    }

    val configuredStreamReadFeatures =
      features(config, "stream-read-features").map {
        case (enumName, value) => StreamReadFeature.valueOf(enumName) -> value
      }
    val streamReadFeatures =
      objectMapperFactory.overrideConfiguredStreamReadFeatures(bindingName, configuredStreamReadFeatures)
    streamReadFeatures.foreach {
      case (feature, value) => factoryBuilder.configure(feature, value)
    }

    val configuredStreamWriteFeatures =
      features(config, "stream-write-features").map {
        case (enumName, value) => StreamWriteFeature.valueOf(enumName) -> value
      }
    val streamWriteFeatures =
      objectMapperFactory.overrideConfiguredStreamWriteFeatures(bindingName, configuredStreamWriteFeatures)
    streamWriteFeatures.foreach {
      case (feature, value) => factoryBuilder.configure(feature, value)
    }

    factoryBuilder
      .streamReadConstraints(streamReadConstraints)
      .streamWriteConstraints(streamWriteConstraints)
      .recyclerPool(getBufferRecyclerPool(config))
      .build()
  }

  private def getBufferRecyclerPool(cfg: Config): RecyclerPool[BufferRecycler] = {
    cfg.getString("buffer-recycler.pool-instance") match {
      case "thread-local"            => JsonRecyclerPools.threadLocalPool()
      case "concurrent-deque"        => JsonRecyclerPools.newConcurrentDequePool()
      case "shared-concurrent-deque" => JsonRecyclerPools.sharedConcurrentDequePool()
      case "bounded"                 => JsonRecyclerPools.newBoundedPool(cfg.getInt("buffer-recycler.bounded-pool-size"))
      case other                     => throw new IllegalArgumentException(s"Unknown recycler-pool: $other")
    }
  }

  private def configureObjectMapperFeatures(
      bindingName: String,
      builder: JsonMapper.Builder,
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config): JsonMapper.Builder = {

    getSerializationFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    getDeserializationFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    getDateTimeFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    getEnumFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    getMapperFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    builder
  }

  private def configureCBORMapperFeatures(
      bindingName: String,
      builder: CBORMapper.Builder,
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config): CBORMapper.Builder = {

    getSerializationFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    getDeserializationFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    getDateTimeFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    getEnumFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    getMapperFeatures(bindingName, config, objectMapperFactory).foreach {
      case (feature, value) => builder.configure(feature, value)
    }

    builder
  }

  private def getSerializationFeatures(bindingName: String,
      config: Config,
      objectMapperFactory: JacksonObjectMapperFactory): Seq[(SerializationFeature, Boolean)] = {
    val configuredSerializationFeatures =
      features(config, "serialization-features").map {
        case (enumName, value) => SerializationFeature.valueOf(enumName) -> value
      }
    objectMapperFactory.overrideConfiguredSerializationFeatures(bindingName, configuredSerializationFeatures)
  }

  private def getDeserializationFeatures(bindingName: String,
      config: Config,
      objectMapperFactory: JacksonObjectMapperFactory): Seq[(DeserializationFeature, Boolean)] = {
    val configuredDeserializationFeatures =
      features(config, "deserialization-features").map {
        case (enumName, value) => DeserializationFeature.valueOf(enumName) -> value
      }
    objectMapperFactory.overrideConfiguredDeserializationFeatures(bindingName, configuredDeserializationFeatures)
  }

  private def getDateTimeFeatures(bindingName: String,
      config: Config,
      objectMapperFactory: JacksonObjectMapperFactory): Seq[(DateTimeFeature, Boolean)] = {
    val configuredDateTimeFeatures =
      features(config, "datetime-features").map {
        case (enumName, value) => DateTimeFeature.valueOf(enumName) -> value
      }
    objectMapperFactory.overrideConfiguredDateTimeFeatures(bindingName, configuredDateTimeFeatures)
  }

  private def getEnumFeatures(bindingName: String,
      config: Config,
      objectMapperFactory: JacksonObjectMapperFactory): Seq[(EnumFeature, Boolean)] = {
    val configuredEnumFeatures =
      features(config, "enum-features").map {
        case (enumName, value) => EnumFeature.valueOf(enumName) -> value
      }
    objectMapperFactory.overrideConfiguredEnumFeatures(bindingName, configuredEnumFeatures)
  }

  private def getMapperFeatures(bindingName: String,
      config: Config,
      objectMapperFactory: JacksonObjectMapperFactory): Seq[(MapperFeature, Boolean)] = {
    val configuredMapperFeatures =
      features(config, "mapper-features").map {
        case (enumName, value) => MapperFeature.valueOf(enumName) -> value
      }
    objectMapperFactory.overrideConfiguredMapperFeatures(bindingName, configuredMapperFeatures)
  }

  private def configureObjectVisibility(
      bindingName: String,
      builder: MapperBuilder[ObjectMapper, _],
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config): MapperBuilder[ObjectMapper, _] = {

    val configuredVisibility: immutable.Seq[(PropertyAccessor, JsonAutoDetect.Visibility)] =
      configPairs(config, "visibility").map {
        case (property, visibility) =>
          PropertyAccessor.valueOf(property) -> JsonAutoDetect.Visibility.valueOf(visibility)
      }
    val visibilitySettings =
      objectMapperFactory.overrideConfiguredVisibility(bindingName, configuredVisibility)
    if (visibilitySettings.isEmpty) {
      builder
    } else {
      builder.changeDefaultVisibility((vc: VisibilityChecker) => {
        var localVc = vc
        visibilitySettings.foreach {
          case (property, visibility) => localVc = localVc.withVisibility(property, visibility)
        }
        localVc
      }).asInstanceOf[MapperBuilder[ObjectMapper, _]]
    }
  }

  private def configureObjectMapperModules(
      bindingName: String,
      builder: MapperBuilder[ObjectMapper, _],
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config,
      dynamicAccess: DynamicAccess,
      log: Option[LoggingAdapter]): MapperBuilder[ObjectMapper, _] = {

    val configuredModules = config.getStringList("jackson-modules").asScala
    val modules = configuredModules.flatMap { fqcn =>
      if (isModuleEnabled(fqcn, dynamicAccess)) {
        dynamicAccess.createInstanceFor[JacksonModule](fqcn, Nil) match {
          case Success(m) => Some(m)
          case Failure(e) =>
            log.foreach(
              _.error(
                e,
                s"Could not load configured Jackson module [$fqcn], " +
                "please verify classpath dependencies or amend the configuration " +
                "[pekko.serialization.jackson3.jackson-modules]. Continuing without this module."))
            None
        }
      } else
        None
    }

    val modules3 = objectMapperFactory.overrideConfiguredModules(bindingName, modules.toList)

    modules3.foreach { module =>
      builder.addModule(module)
      log.foreach(_.debug("Registered Jackson module [{}]", module.getClass.getName))
    }

    builder
  }

  private def configPairs(config: Config, section: String): immutable.Seq[(String, String)] = {
    val cfg = config.getConfig(section)
    cfg.root.keySet().asScala.map(key => key -> cfg.getString(key)).toList
  }

  /**
   * INTERNAL API: Use [[JacksonObjectMapperProvider#create]]
   *
   * This is needed by one test in Lagom where the ObjectMapper is created without starting and ActorSystem.
   */
  @InternalStableApi
  def createObjectMapper(
      bindingName: String,
      jsonFactory: Option[JsonFactory],
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config,
      dynamicAccess: DynamicAccess,
      log: Option[LoggingAdapter]): ObjectMapper = {

    val configuredJsonFactory = createJsonFactory(bindingName, objectMapperFactory, config, jsonFactory)
    val builder = objectMapperFactory.newObjectMapperBuilder(configuredJsonFactory)
    configureObjectMapperFeatures(bindingName, builder, objectMapperFactory, config)
    configureObjectMapperModules(bindingName, builder.asInstanceOf[MapperBuilder[ObjectMapper, _]],
      objectMapperFactory, config, dynamicAccess, log)
    configureObjectVisibility(bindingName, builder.asInstanceOf[MapperBuilder[ObjectMapper, _]],
      objectMapperFactory, config)
    builder.build()
  }

  def createCBORMapper(
      bindingName: String,
      streamFactory: Option[CBORFactory],
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config,
      dynamicAccess: DynamicAccess,
      log: Option[LoggingAdapter]): ObjectMapper = {

    val configuredJsonFactory = createCBORFactory(bindingName, objectMapperFactory, config, streamFactory)
    val builder = objectMapperFactory.newCBORMapperBuilder(configuredJsonFactory)
    configureCBORMapperFeatures(bindingName, builder, objectMapperFactory, config)
    configureObjectMapperModules(bindingName, builder.asInstanceOf[MapperBuilder[ObjectMapper, _]],
      objectMapperFactory, config,
      dynamicAccess, log)
    configureObjectVisibility(bindingName, builder.asInstanceOf[MapperBuilder[ObjectMapper, _]],
      objectMapperFactory, config)
    builder.build()
  }

  private def isModuleEnabled(fqcn: String, dynamicAccess: DynamicAccess): Boolean =
    fqcn match {
      case "com.github.pjfanning.pekko.serialization.jackson3.PekkoTypedJacksonModule" =>
        // pekko-actor-typed dependency is "provided" and may not be included
        dynamicAccess.classIsOnClasspath("org.apache.pekko.actor.typed.ActorRef")
      case "com.github.pjfanning.pekko.serialization.jackson3.PekkoStreamJacksonModule" =>
        // pekko-stream dependency is "provided" and may not be included
        dynamicAccess.classIsOnClasspath("org.apache.pekko.stream.Graph")
      case _ => true
    }

  private def features(config: Config, section: String): immutable.Seq[(String, Boolean)] = {
    val cfg = config.getConfig(section)
    cfg.root.keySet().asScala.map(key => key -> cfg.getBoolean(key)).toList
  }
}

/**
 * Registry of shared `ObjectMapper` instances, each with its unique `bindingName`.
 */
final class JacksonObjectMapperProvider(system: ExtendedActorSystem) extends Extension {
  private val objectMappers = new ConcurrentHashMap[String, ObjectMapper]

  /**
   * Scala API: Returns an existing Jackson `ObjectMapper` that was created previously with this method, or
   * creates a new instance.
   *
   * The `ObjectMapper` is created with sensible defaults and modules configured
   * in `pekko.serialization.jackson3.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
   * if the `ActorSystem` is started with such [[pekko.actor.setup.ActorSystemSetup]].
   *
   * The returned `ObjectMapper` must not be modified, because it may already be in use and such
   * modifications are not thread-safe.
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `JsonFactory`, for plain JSON `None` (defaults)
   *                    can be used
   */
  def getOrCreate(bindingName: String, jsonFactory: Option[JsonFactory]): ObjectMapper = {
    objectMappers.computeIfAbsent(bindingName, _ => create(bindingName, jsonFactory))
  }

  /**
   * Scala API: Returns an existing Jackson `ObjectMapper` that was created previously with this method, or
   * creates a new instance.
   *
   * The `ObjectMapper` is created with sensible defaults and modules configured
   * in `pekko.serialization.jackson3.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
   * if the `ActorSystem` is started with such [[pekko.actor.setup.ActorSystemSetup]].
   *
   * The returned `ObjectMapper` must not be modified, because it may already be in use and such
   * modifications are not thread-safe.
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `CBORFactory`
   */
  def getOrCreateCBOR(bindingName: String, jsonFactory: Option[CBORFactory]): ObjectMapper = {
    objectMappers.computeIfAbsent(bindingName, _ => createCBOR(bindingName, jsonFactory))
  }

  /**
   * Java API: Returns an existing Jackson `ObjectMapper` that was created previously with this method, or
   * creates a new instance.
   *
   * The `ObjectMapper` is created with sensible defaults and modules configured
   * in `pekko.serialization.jackson3.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
   * if the `ActorSystem` is started with such [[pekko.actor.setup.ActorSystemSetup]].
   *
   * The returned `ObjectMapper` must not be modified, because it may already be in use and such
   * modifications are not thread-safe.
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `JsonFactory` such as `CBORFactory`, for plain JSON `None` (defaults)
   *                    can be used
   */
  def getOrCreate(bindingName: String, jsonFactory: Optional[JsonFactory]): ObjectMapper =
    getOrCreate(bindingName, jsonFactory.toScala)

  /**
   * Scala API: Creates a new instance of a Jackson `ObjectMapper` with sensible defaults and modules configured
   * in `pekko.serialization.jackson3.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
   * if the `ActorSystem` is started with such [[pekko.actor.setup.ActorSystemSetup]].
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `JsonFactory`, for plain JSON `None` (defaults)
   *                    can be used
   * @see [[JacksonObjectMapperProvider#getOrCreate]]
   */
  def create(bindingName: String, jsonFactory: Option[JsonFactory]): ObjectMapper = {
    val log = Logging.getLogger(system, JacksonObjectMapperProvider.getClass)
    val dynamicAccess = system.dynamicAccess

    val config = JacksonObjectMapperProvider.configForBinding(bindingName, system.settings.config)

    val factory = system.settings.setup.get[JacksonObjectMapperProviderSetup] match {
      case Some(setup) => setup.factory
      case None        => new JacksonObjectMapperFactory // default
    }

    JacksonObjectMapperProvider.createObjectMapper(bindingName, jsonFactory, factory, config, dynamicAccess, Some(log))
  }

  /**
   * Scala API: Creates a new instance of a Jackson `ObjectMapper` with sensible defaults and modules configured
   * in `pekko.serialization.jackson3.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
   * if the `ActorSystem` is started with such [[pekko.actor.setup.ActorSystemSetup]].
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `CBORFactory`
   * @see [[JacksonObjectMapperProvider#getOrCreate]]
   */
  def createCBOR(bindingName: String, streamFactory: Option[CBORFactory]): ObjectMapper = {
    val log = Logging.getLogger(system, JacksonObjectMapperProvider.getClass)
    val dynamicAccess = system.dynamicAccess

    val config = JacksonObjectMapperProvider.configForBinding(bindingName, system.settings.config)

    val factory = system.settings.setup.get[JacksonObjectMapperProviderSetup] match {
      case Some(setup) => setup.factory
      case None        => new JacksonObjectMapperFactory // default
    }

    JacksonObjectMapperProvider.createCBORMapper(bindingName, streamFactory, factory, config, dynamicAccess, Some(log))
  }

  /**
   * Java API: Creates a new instance of a Jackson `ObjectMapper` with sensible defaults and modules configured
   * in `pekko.serialization.jackson3.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
   * if the `ActorSystem` is started with such [[pekko.actor.setup.ActorSystemSetup]].
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `JsonFactory` such as `CBORFactory`, for plain JSON `None` (defaults)
   *                    can be used
   * @see [[JacksonObjectMapperProvider#getOrCreate]]
   */
  def create(bindingName: String, jsonFactory: Optional[JsonFactory]): ObjectMapper =
    create(bindingName, jsonFactory.toScala)

}

object JacksonObjectMapperProviderSetup {

  /**
   * Scala API: factory for defining a `JacksonObjectMapperProvider` that is passed in when ActorSystem
   * is created rather than creating one from configured class name.
   */
  def apply(factory: JacksonObjectMapperFactory): JacksonObjectMapperProviderSetup =
    new JacksonObjectMapperProviderSetup(factory)

  /**
   * Java API: factory for defining a `JacksonObjectMapperProvider` that is passed in when ActorSystem
   * is created rather than creating one from configured class name.
   */
  def create(factory: JacksonObjectMapperFactory): JacksonObjectMapperProviderSetup =
    apply(factory)

}

/**
 * Setup for defining a `JacksonObjectMapperProvider` that can be passed in when ActorSystem
 * is created rather than creating one from configured class name. Create a subclass of
 * [[JacksonObjectMapperFactory]] and override the methods to amend the defaults.
 */
final class JacksonObjectMapperProviderSetup(val factory: JacksonObjectMapperFactory) extends Setup

/**
 * Used with [[JacksonObjectMapperProviderSetup]] for defining a `JacksonObjectMapperProvider` that can be
 * passed in when ActorSystem is created rather than creating one from configured class name.
 * Create a subclass and override the methods to amend the defaults.
 */
class JacksonObjectMapperFactory {

  // TODO fix scaladoc

  def newObjectMapperBuilder(jsonFactory: JsonFactory): JsonMapper.Builder =
    JsonMapper.builder(jsonFactory)

  def newCBORMapperBuilder(factory: CBORFactory): CBORMapper.Builder =
    CBORMapper.builder(factory)

  /**
   * After construction of the `ObjectMapper` the configured modules are added to
   * the mapper. These modules can be amended programmatically by overriding this method and
   * return the modules that are to be applied to the `ObjectMapper`.
   *
   * When implementing a `JacksonObjectMapperFactory` with Java the `immutable.Seq` can be
   * created with [[pekko.japi.Util.immutableSeq]].
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredModules the list of `Modules` that were configured in
   *                           `pekko.serialization.jackson3.deserialization-features`
   */
  def overrideConfiguredModules(
      @nowarn("msg=never used") bindingName: String,
      configuredModules: immutable.Seq[JacksonModule]): immutable.Seq[JacksonModule] =
    configuredModules

  /**
   * After construction of the `ObjectMapper` the configured serialization features are applied to
   * the mapper. These features can be amended programatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * When implementing a `JacksonObjectMapperFactory` with Java the `immutable.Seq` can be
   * created with [[pekko.japi.Util.immutableSeq]].
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `SerializationFeature` that were configured in
   *                           `pekko.serialization.jackson3.serialization-features`
   */
  def overrideConfiguredSerializationFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(SerializationFeature, Boolean)])
      : immutable.Seq[(SerializationFeature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured deserialization features are applied to
   * the mapper. These features can be amended programatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * When implementing a `JacksonObjectMapperFactory` with Java the `immutable.Seq` can be
   * created with [[pekko.japi.Util.immutableSeq]].
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `DeserializationFeature` that were configured in
   *                           `pekko.serialization.jackson3.deserialization-features`
   */
  def overrideConfiguredDeserializationFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(DeserializationFeature, Boolean)])
      : immutable.Seq[(DeserializationFeature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured datetime features are applied to
   * the mapper. These features can be amended programatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * When implementing a `JacksonObjectMapperFactory` with Java the `immutable.Seq` can be
   * created with [[pekko.japi.Util.immutableSeq]].
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `DateTimeFeature` that were configured in
   *                           `pekko.serialization.jackson3.datetime-features`
   */
  def overrideConfiguredDateTimeFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(DateTimeFeature, Boolean)])
      : immutable.Seq[(DateTimeFeature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured enum features are applied to
   * the mapper. These features can be amended programatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * When implementing a `JacksonObjectMapperFactory` with Java the `immutable.Seq` can be
   * created with [[pekko.japi.Util.immutableSeq]].
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `DateTimeFeature` that were configured in
   *                           `pekko.serialization.jackson3.enum-features`
   */
  def overrideConfiguredEnumFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(EnumFeature, Boolean)])
      : immutable.Seq[(EnumFeature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured mapper features are applied to
   * the mapper. These features can be amended programmatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `MapperFeatures` that were configured in `pekko.serialization.jackson3.mapper-features`
   */
  def overrideConfiguredMapperFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(MapperFeature, Boolean)]): immutable.Seq[(MapperFeature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured JSON parser features are applied to
   * the mapper. These features can be amended programmatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `StreamReadFeature` that were configured in `pekko.serialization.jackson3.stream-read-features`
   */
  def overrideConfiguredJsonParserFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(StreamReadFeature, Boolean)]): immutable.Seq[(StreamReadFeature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured JSON generator features are applied to
   * the mapper. These features can be amended programmatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `StreamWriteFeature` that were configured in `pekko.serialization.jackson3.json-generator-features`
   */
  def overrideConfiguredJsonGeneratorFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(StreamWriteFeature, Boolean)])
      : immutable.Seq[(StreamWriteFeature, Boolean)] =
    configuredFeatures

  /**
   * `StreamReadFeature`s used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These features can be amended programmatically by overriding this method and return the features
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `StreamReadFeature` that were configured in `pekko.serialization.jackson3.stream-read-features`
   */
  def overrideConfiguredStreamReadFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(StreamReadFeature, Boolean)]): immutable.Seq[(StreamReadFeature, Boolean)] =
    configuredFeatures

  /**
   * `StreamWriteFeature`s used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These features can be amended programmatically by overriding this method and return the features
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `StreamWriterFeature` that were configured in `pekko.serialization.jackson3.stream-write-features`
   */
  def overrideConfiguredStreamWriteFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(StreamWriteFeature, Boolean)]): immutable.Seq[(StreamWriteFeature, Boolean)] =
    configuredFeatures

  /**
   * `JsonReadFeature`s used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These features can be amended programmatically by overriding this method and return the features
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `JsonReadFeature` that were configured in `pekko.serialization.jackson3.json-read-features`
   */
  def overrideConfiguredJsonReadFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(JsonReadFeature, Boolean)]): immutable.Seq[(JsonReadFeature, Boolean)] =
    configuredFeatures

  /**
   * `JsonWriteFeature`s used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These features can be amended programmatically by overriding this method and return the features
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `JsonWriteFeature` that were configured in `pekko.serialization.jackson3.json-write-features`
   */
  def overrideConfiguredJsonWriteFeatures(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(JsonWriteFeature, Boolean)]): immutable.Seq[(JsonWriteFeature, Boolean)] =
    configuredFeatures

  /**
   * Visibility settings used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These settings can be amended programmatically by overriding this method and return the values
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `PropertyAccessor`/`JsonAutoDetect.Visibility` that were configured in
   *                           `pekko.serialization.jackson3.visibility`
   */
  def overrideConfiguredVisibility(
      @nowarn("msg=never used") bindingName: String,
      configuredFeatures: immutable.Seq[(PropertyAccessor, JsonAutoDetect.Visibility)])
      : immutable.Seq[(PropertyAccessor, JsonAutoDetect.Visibility)] =
    configuredFeatures

}
