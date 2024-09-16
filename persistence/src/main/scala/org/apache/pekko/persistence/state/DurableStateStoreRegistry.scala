/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.state

import scala.reflect.ClassTag

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.annotation.InternalApi
import pekko.persistence.Persistence
import pekko.persistence.PersistencePlugin
import pekko.persistence.PluginProvider
import pekko.persistence.state.scaladsl.DurableStateStore
import pekko.util.unused

/**
 * Persistence extension for queries.
 */
object DurableStateStoreRegistry extends ExtensionId[DurableStateStoreRegistry] with ExtensionIdProvider {

  override def get(system: ActorSystem): DurableStateStoreRegistry = super.get(system)
  override def get(system: ClassicActorSystemProvider): DurableStateStoreRegistry = super.get(system)

  def createExtension(system: ExtendedActorSystem): DurableStateStoreRegistry = new DurableStateStoreRegistry(system)

  def lookup: DurableStateStoreRegistry.type = DurableStateStoreRegistry

  @InternalApi
  private[pekko] val pluginProvider
      : PluginProvider[DurableStateStoreProvider, DurableStateStore[_], javadsl.DurableStateStore[_]] =
    new PluginProvider[DurableStateStoreProvider, scaladsl.DurableStateStore[_], javadsl.DurableStateStore[_]] {
      override def scalaDsl(t: DurableStateStoreProvider): DurableStateStore[_] = t.scaladslDurableStateStore()
      override def javaDsl(t: DurableStateStoreProvider): javadsl.DurableStateStore[_] = t.javadslDurableStateStore()
    }

}

class DurableStateStoreRegistry(system: ExtendedActorSystem)
    extends PersistencePlugin[scaladsl.DurableStateStore[_], javadsl.DurableStateStore[_], DurableStateStoreProvider](
      system)(ClassTag(classOf[DurableStateStoreProvider]), DurableStateStoreRegistry.pluginProvider)
    with Extension {

  private val systemConfig = system.settings.config

  private lazy val defaultPluginId = {
    val configPath = systemConfig.getString("pekko.persistence.state.plugin")
    Persistence.verifyPluginConfigIsDefined(configPath, "Default DurableStateStore")
    Persistence.verifyPluginConfigExists(systemConfig, configPath, "DurableStateStore")
    configPath
  }

  private def pluginIdOrDefault(pluginId: String): String = {
    val configPath = if (isEmpty(pluginId)) defaultPluginId else pluginId
    Persistence.verifyPluginConfigExists(systemConfig, configPath, "DurableStateStore")
    configPath
  }

  private def pluginConfig(pluginId: String): Config = {
    val configPath = pluginIdOrDefault(pluginId)
    systemConfig.getConfig(configPath).withFallback(systemConfig.getConfig("pekko.persistence.state-plugin-fallback"))
  }

  /** Check for default or missing identity. */
  private def isEmpty(text: String) =
    text == null || text.isEmpty

  /**
   * Scala API: Returns the [[pekko.persistence.state.scaladsl.DurableStateStore]] specified by the given
   * configuration entry.
   */
  final def durableStateStoreFor[T <: scaladsl.DurableStateStore[_]](pluginId: String): T =
    pluginFor(pluginIdOrDefault(pluginId), pluginConfig(pluginId)).scaladslPlugin.asInstanceOf[T]

  /**
   * Java API: Returns the [[pekko.persistence.state.javadsl.DurableStateStore]] specified by the given
   * configuration entry.
   */
  final def getDurableStateStoreFor[T <: javadsl.DurableStateStore[_]](
      @unused clazz: Class[T], // FIXME generic Class could be problematic in Java
      pluginId: String): T =
    pluginFor(pluginIdOrDefault(pluginId), pluginConfig(pluginId)).javadslPlugin.asInstanceOf[T]

}
