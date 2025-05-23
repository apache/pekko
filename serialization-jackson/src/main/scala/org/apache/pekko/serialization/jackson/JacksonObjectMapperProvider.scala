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

package org.apache.pekko.serialization.jackson

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.nowarn
import scala.collection.immutable
import scala.util.{ Failure, Success }
import com.fasterxml.jackson.annotation.{ JsonAutoDetect, JsonCreator, PropertyAccessor }
import com.fasterxml.jackson.core.{
  JsonFactory,
  JsonFactoryBuilder,
  JsonGenerator,
  JsonParser,
  StreamReadConstraints,
  StreamReadFeature,
  StreamWriteConstraints,
  StreamWriteFeature
}
import com.fasterxml.jackson.core.json.{ JsonReadFeature, JsonWriteFeature }
import com.fasterxml.jackson.core.util.{ BufferRecycler, JsonRecyclerPools, RecyclerPool }
import com.fasterxml.jackson.databind.{
  DeserializationFeature,
  MapperFeature,
  Module,
  ObjectMapper,
  SerializationFeature
}
import com.fasterxml.jackson.databind.cfg.EnumFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.typesafe.config.Config
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
import pekko.util.unused
import pekko.util.OptionConverters._

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
    val basePath = "pekko.serialization.jackson"
    val baseConf = systemConfig.getConfig(basePath)
    if (systemConfig.hasPath(s"$basePath.$bindingName"))
      systemConfig.getConfig(s"$basePath.$bindingName").withFallback(baseConf)
    else
      baseConf
  }

  private def createJsonFactory(
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
      .maxTokenCount(config.getLong("read.max-token-count"))
      .build()

    val streamWriteConstraints = StreamWriteConstraints.builder()
      .maxNestingDepth(config.getInt("write.max-nesting-depth"))
      .build()

    val jsonFactory: JsonFactory = baseJsonFactory match {
      case Some(factory) =>
        // Issue #28918 not possible to use new JsonFactoryBuilder(jsonFactory) here.
        // It doesn't preserve the formatParserFeatures and formatGeneratorFeatures in
        // CBORFactor. Therefore we use JsonFactory and configure the features with mappedFeature
        // instead of using JsonFactoryBuilder (new in Jackson 2.10.0).
        factory.setStreamReadConstraints(streamReadConstraints)
        factory.setStreamWriteConstraints(streamWriteConstraints)
        factory.setRecyclerPool(getBufferRecyclerPool(config))
      case None =>
        new JsonFactoryBuilder()
          .streamReadConstraints(streamReadConstraints)
          .streamWriteConstraints(streamWriteConstraints)
          .recyclerPool(getBufferRecyclerPool(config))
          .build()
    }

    val configuredStreamReadFeatures =
      features(config, "stream-read-features").map {
        case (enumName, value) => StreamReadFeature.valueOf(enumName) -> value
      }
    val streamReadFeatures =
      objectMapperFactory.overrideConfiguredStreamReadFeatures(bindingName, configuredStreamReadFeatures)
    streamReadFeatures.foreach {
      case (feature, value) => jsonFactory.configure(feature.mappedFeature, value)
    }

    val configuredStreamWriteFeatures =
      features(config, "stream-write-features").map {
        case (enumName, value) => StreamWriteFeature.valueOf(enumName) -> value
      }
    val streamWriteFeatures =
      objectMapperFactory.overrideConfiguredStreamWriteFeatures(bindingName, configuredStreamWriteFeatures)
    streamWriteFeatures.foreach {
      case (feature, value) => jsonFactory.configure(feature.mappedFeature, value)
    }

    val configuredJsonReadFeatures =
      features(config, "json-read-features").map {
        case (enumName, value) => JsonReadFeature.valueOf(enumName) -> value
      }
    val jsonReadFeatures =
      objectMapperFactory.overrideConfiguredJsonReadFeatures(bindingName, configuredJsonReadFeatures)
    jsonReadFeatures.foreach {
      case (feature, value) => jsonFactory.configure(feature.mappedFeature, value)
    }

    val configuredJsonWriteFeatures =
      features(config, "json-write-features").map {
        case (enumName, value) => JsonWriteFeature.valueOf(enumName) -> value
      }
    val jsonWriteFeatures =
      objectMapperFactory.overrideConfiguredJsonWriteFeatures(bindingName, configuredJsonWriteFeatures)
    jsonWriteFeatures.foreach {
      case (feature, value) => jsonFactory.configure(feature.mappedFeature, value)
    }

    jsonFactory
  }

  private def getBufferRecyclerPool(cfg: Config): RecyclerPool[BufferRecycler] = {
    cfg.getString("buffer-recycler.pool-instance") match {
      case "thread-local"            => JsonRecyclerPools.threadLocalPool()
      case "concurrent-deque"        => JsonRecyclerPools.newConcurrentDequePool()
      case "shared-concurrent-deque" => JsonRecyclerPools.sharedConcurrentDequePool()
      case "bounded"                 => JsonRecyclerPools.newBoundedPool(cfg.getInt("buffer-recycler.bounded-pool-size"))
      case "non-recycling"           => JsonRecyclerPools.nonRecyclingPool()
      case other                     => throw new IllegalArgumentException(s"Unknown recycler-pool: $other")
    }
  }

  @nowarn("msg=deprecated")
  private def configureObjectMapperFeatures(
      bindingName: String,
      objectMapper: ObjectMapper,
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config): Unit = {

    val configuredSerializationFeatures =
      features(config, "serialization-features").map {
        case (enumName, value) => SerializationFeature.valueOf(enumName) -> value
      }
    val serializationFeatures =
      objectMapperFactory.overrideConfiguredSerializationFeatures(bindingName, configuredSerializationFeatures)
    serializationFeatures.foreach {
      case (feature, value) => objectMapper.configure(feature, value)
    }

    val configuredDeserializationFeatures =
      features(config, "deserialization-features").map {
        case (enumName, value) => DeserializationFeature.valueOf(enumName) -> value
      }
    val deserializationFeatures =
      objectMapperFactory.overrideConfiguredDeserializationFeatures(bindingName, configuredDeserializationFeatures)
    deserializationFeatures.foreach {
      case (feature, value) => objectMapper.configure(feature, value)
    }

    val configuredEnumFeatures = features(config, "enum-features").map {
      case (enumName, value) => EnumFeature.valueOf(enumName) -> value
    }
    val enumFeatures = objectMapperFactory.overrideConfiguredEnumFeatures(bindingName, configuredEnumFeatures)
    enumFeatures.foreach {
      case (feature, value) => objectMapper.configure(feature, value)
    }

    val configuredMapperFeatures = features(config, "mapper-features").map {
      case (enumName, value) => MapperFeature.valueOf(enumName) -> value
    }
    val mapperFeatures = objectMapperFactory.overrideConfiguredMapperFeatures(bindingName, configuredMapperFeatures)
    mapperFeatures.foreach {
      case (feature, value) => objectMapper.configure(feature, value)
    }

    val configuredJsonParserFeatures = features(config, "json-parser-features").map {
      case (enumName, value) => JsonParser.Feature.valueOf(enumName) -> value
    }
    val jsonParserFeatures =
      objectMapperFactory.overrideConfiguredJsonParserFeatures(bindingName, configuredJsonParserFeatures)
    jsonParserFeatures.foreach {
      case (feature, value) => objectMapper.configure(feature, value)
    }

    val configuredJsonGeneratorFeatures = features(config, "json-generator-features").map {
      case (enumName, value) => JsonGenerator.Feature.valueOf(enumName) -> value
    }
    val jsonGeneratorFeatures =
      objectMapperFactory.overrideConfiguredJsonGeneratorFeatures(bindingName, configuredJsonGeneratorFeatures)
    jsonGeneratorFeatures.foreach {
      case (feature, value) => objectMapper.configure(feature, value)
    }
  }

  private def configureObjectVisibility(
      bindingName: String,
      objectMapper: ObjectMapper,
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config): Unit = {

    val configuredVisibility: immutable.Seq[(PropertyAccessor, JsonAutoDetect.Visibility)] =
      configPairs(config, "visibility").map {
        case (property, visibility) =>
          PropertyAccessor.valueOf(property) -> JsonAutoDetect.Visibility.valueOf(visibility)
      }
    val visibility =
      objectMapperFactory.overrideConfiguredVisibility(bindingName, configuredVisibility)
    visibility.foreach {
      case (property, visibility) => objectMapper.setVisibility(property, visibility)
    }

  }

  private def configureObjectMapperModules(
      bindingName: String,
      objectMapper: ObjectMapper,
      objectMapperFactory: JacksonObjectMapperFactory,
      config: Config,
      dynamicAccess: DynamicAccess,
      log: Option[LoggingAdapter]): Unit = {

    import pekko.util.ccompat.JavaConverters._

    val configuredModules = config.getStringList("jackson-modules").asScala
    val modules1 =
      configuredModules.flatMap { fqcn =>
        if (isModuleEnabled(fqcn, dynamicAccess)) {
          dynamicAccess.createInstanceFor[Module](fqcn, Nil) match {
            case Success(m) => Some(m)
            case Failure(e) =>
              log.foreach(
                _.error(
                  e,
                  s"Could not load configured Jackson module [$fqcn], " +
                  "please verify classpath dependencies or amend the configuration " +
                  "[pekko.serialization.jackson.jackson-modules]. Continuing without this module."))
              None
          }
        } else
          None
      }

    val modules2 = modules1.map { module =>
      if (module.isInstanceOf[ParameterNamesModule])
        // ParameterNamesModule needs a special case for the constructor to ensure that single-parameter
        // constructors are handled the same way as constructors with multiple parameters.
        // See https://github.com/FasterXML/jackson-module-parameter-names#delegating-creator
        new ParameterNamesModule(JsonCreator.Mode.PROPERTIES)
      else module
    }.toList

    val modules3 = objectMapperFactory.overrideConfiguredModules(bindingName, modules2)

    modules3.foreach { module =>
      objectMapper.registerModule(module)
      log.foreach(_.debug("Registered Jackson module [{}]", module.getClass.getName))
    }
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
    val mapper = objectMapperFactory.newObjectMapper(bindingName, configuredJsonFactory)

    configureObjectMapperFeatures(bindingName, mapper, objectMapperFactory, config)
    configureObjectMapperModules(bindingName, mapper, objectMapperFactory, config, dynamicAccess, log)
    configureObjectVisibility(bindingName, mapper, objectMapperFactory, config)

    mapper
  }

  private def isModuleEnabled(fqcn: String, dynamicAccess: DynamicAccess): Boolean =
    fqcn match {
      case "org.apache.pekko.serialization.jackson.PekkoTypedJacksonModule" =>
        // pekko-actor-typed dependency is "provided" and may not be included
        dynamicAccess.classIsOnClasspath("org.apache.pekko.actor.typed.ActorRef")
      case "org.apache.pekko.serialization.jackson.PekkoStreamJacksonModule" =>
        // pekko-stream dependency is "provided" and may not be included
        dynamicAccess.classIsOnClasspath("org.apache.pekko.stream.Graph")
      case _ => true
    }

  private def features(config: Config, section: String): immutable.Seq[(String, Boolean)] = {
    import pekko.util.ccompat.JavaConverters._
    val cfg = config.getConfig(section)
    cfg.root.keySet().asScala.map(key => key -> cfg.getBoolean(key)).toList
  }

  private def configPairs(config: Config, section: String): immutable.Seq[(String, String)] = {
    import pekko.util.ccompat.JavaConverters._
    val cfg = config.getConfig(section)
    cfg.root.keySet().asScala.map(key => key -> cfg.getString(key)).toList
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
   * in `pekko.serialization.jackson.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
   * if the `ActorSystem` is started with such [[pekko.actor.setup.ActorSystemSetup]].
   *
   * The returned `ObjectMapper` must not be modified, because it may already be in use and such
   * modifications are not thread-safe.
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `JsonFactory` such as `CBORFactory`, for plain JSON `None` (defaults)
   *                    can be used
   */
  def getOrCreate(bindingName: String, jsonFactory: Option[JsonFactory]): ObjectMapper = {
    objectMappers.computeIfAbsent(bindingName, _ => create(bindingName, jsonFactory))
  }

  /**
   * Java API: Returns an existing Jackson `ObjectMapper` that was created previously with this method, or
   * creates a new instance.
   *
   * The `ObjectMapper` is created with sensible defaults and modules configured
   * in `pekko.serialization.jackson.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
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
   * in `pekko.serialization.jackson.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
   * if the `ActorSystem` is started with such [[pekko.actor.setup.ActorSystemSetup]].
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `JsonFactory` such as `CBORFactory`, for plain JSON `None` (defaults)
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
   * Java API: Creates a new instance of a Jackson `ObjectMapper` with sensible defaults and modules configured
   * in `pekko.serialization.jackson.jackson-modules`. It's using [[JacksonObjectMapperProviderSetup]]
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

  /**
   * Override this method to create a new custom instance of `ObjectMapper` for the given `serializerIdentifier`.
   *
   * @param bindingName name of this `ObjectMapper`
   * @param jsonFactory optional `JsonFactory` such as `CBORFactory`, for plain JSON `None` (defaults)
   *                    can be used
   */
  def newObjectMapper(@unused bindingName: String, jsonFactory: JsonFactory): ObjectMapper =
    JsonMapper.builder(jsonFactory).build()

  /**
   * After construction of the `ObjectMapper` the configured modules are added to
   * the mapper. These modules can be amended programatically by overriding this method and
   * return the modules that are to be applied to the `ObjectMapper`.
   *
   * When implementing a `JacksonObjectMapperFactory` with Java the `immutable.Seq` can be
   * created with [[pekko.japi.Util.immutableSeq]].
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredModules the list of `Modules` that were configured in
   *                           `pekko.serialization.jackson.deserialization-features`
   */
  def overrideConfiguredModules(
      @unused bindingName: String,
      configuredModules: immutable.Seq[Module]): immutable.Seq[Module] =
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
   *                           `pekko.serialization.jackson.serialization-features`
   */
  def overrideConfiguredSerializationFeatures(
      @unused bindingName: String,
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
   *                           `pekko.serialization.jackson.deserialization-features`
   */
  def overrideConfiguredDeserializationFeatures(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(DeserializationFeature, Boolean)])
      : immutable.Seq[(DeserializationFeature, Boolean)] =
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
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(EnumFeature, Boolean)])
      : immutable.Seq[(EnumFeature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured mapper features are applied to
   * the mapper. These features can be amended programmatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `MapperFeatures` that were configured in `pekko.serialization.jackson.mapper-features`
   */
  def overrideConfiguredMapperFeatures(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(MapperFeature, Boolean)]): immutable.Seq[(MapperFeature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured JSON parser features are applied to
   * the mapper. These features can be amended programmatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `JsonParser.Feature` that were configured in `pekko.serialization.jackson.json-parser-features`
   */
  def overrideConfiguredJsonParserFeatures(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(JsonParser.Feature, Boolean)]): immutable.Seq[(JsonParser.Feature, Boolean)] =
    configuredFeatures

  /**
   * After construction of the `ObjectMapper` the configured JSON generator features are applied to
   * the mapper. These features can be amended programmatically by overriding this method and
   * return the features that are to be applied to the `ObjectMapper`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `JsonGenerator.Feature` that were configured in `pekko.serialization.jackson.json-generator-features`
   */
  def overrideConfiguredJsonGeneratorFeatures(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(JsonGenerator.Feature, Boolean)])
      : immutable.Seq[(JsonGenerator.Feature, Boolean)] =
    configuredFeatures

  /**
   * `StreamReadFeature`s used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These features can be amended programmatically by overriding this method and return the features
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `StreamReadFeature` that were configured in `pekko.serialization.jackson.stream-read-features`
   */
  def overrideConfiguredStreamReadFeatures(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(StreamReadFeature, Boolean)]): immutable.Seq[(StreamReadFeature, Boolean)] =
    configuredFeatures

  /**
   * `StreamWriteFeature`s used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These features can be amended programmatically by overriding this method and return the features
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `StreamWriterFeature` that were configured in `pekko.serialization.jackson.stream-write-features`
   */
  def overrideConfiguredStreamWriteFeatures(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(StreamWriteFeature, Boolean)]): immutable.Seq[(StreamWriteFeature, Boolean)] =
    configuredFeatures

  /**
   * `JsonReadFeature`s used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These features can be amended programmatically by overriding this method and return the features
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `JsonReadFeature` that were configured in `pekko.serialization.jackson.json-read-features`
   */
  def overrideConfiguredJsonReadFeatures(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(JsonReadFeature, Boolean)]): immutable.Seq[(JsonReadFeature, Boolean)] =
    configuredFeatures

  /**
   * `JsonWriteFeature`s used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These features can be amended programmatically by overriding this method and return the features
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `JsonWriteFeature` that were configured in `pekko.serialization.jackson.json-write-features`
   */
  def overrideConfiguredJsonWriteFeatures(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(JsonWriteFeature, Boolean)]): immutable.Seq[(JsonWriteFeature, Boolean)] =
    configuredFeatures

  /**
   * Visibility settings used to configure the `JsonFactoryBuilder` that, if provided, will later be used to create
   * an `ObjectMapper`. These settings can be amended programmatically by overriding this method and return the values
   * that are to be applied to the `JsonFactoryBuilder`.
   *
   * @param bindingName bindingName name of this `ObjectMapper`
   * @param configuredFeatures the list of `PropertyAccessor`/`JsonAutoDetect.Visibility` that were configured in
   *                           `pekko.serialization.jackson.visibility`
   */
  def overrideConfiguredVisibility(
      @unused bindingName: String,
      configuredFeatures: immutable.Seq[(PropertyAccessor, JsonAutoDetect.Visibility)])
      : immutable.Seq[(PropertyAccessor, JsonAutoDetect.Visibility)] =
    configuredFeatures

}
