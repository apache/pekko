/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.internal

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.persistence.Persistence

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object DurableStateSettings {

  def apply(system: ActorSystem[_], durableStateStorePluginId: String): DurableStateSettings =
    apply(system.settings.config, durableStateStorePluginId)

  def apply(config: Config, durableStateStorePluginId: String): DurableStateSettings = {
    val typedConfig = config.getConfig("pekko.persistence.typed")

    val stashOverflowStrategy = typedConfig.getString("stash-overflow-strategy").toLowerCase match {
      case "drop" => StashOverflowStrategy.Drop
      case "fail" => StashOverflowStrategy.Fail
      case unknown =>
        throw new IllegalArgumentException(s"Unknown value for stash-overflow-strategy: [$unknown]")
    }

    val stashCapacity = typedConfig.getInt("stash-capacity")
    require(stashCapacity > 0, "stash-capacity MUST be > 0, unbounded buffering is not supported.")

    val logOnStashing = typedConfig.getBoolean("log-stashing")

    val durableStateStoreConfig = durableStateStoreConfigFor(config, durableStateStorePluginId)
    val recoveryTimeout: FiniteDuration =
      durableStateStoreConfig.getDuration("recovery-timeout", TimeUnit.MILLISECONDS).millis

    val useContextLoggerForInternalLogging = typedConfig.getBoolean("use-context-logger-for-internal-logging")

    DurableStateSettings(
      stashCapacity = stashCapacity,
      stashOverflowStrategy,
      logOnStashing = logOnStashing,
      recoveryTimeout,
      durableStateStorePluginId,
      useContextLoggerForInternalLogging)
  }

  private def durableStateStoreConfigFor(config: Config, pluginId: String): Config = {
    def defaultPluginId = {
      val configPath = config.getString("pekko.persistence.state.plugin")
      Persistence.verifyPluginConfigIsDefined(configPath, "Default DurableStateStore")
      configPath
    }

    val configPath = if (pluginId == "") defaultPluginId else pluginId
    Persistence.verifyPluginConfigExists(config, configPath, "DurableStateStore")
    config.getConfig(configPath).withFallback(config.getConfig("pekko.persistence.state-plugin-fallback"))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class DurableStateSettings(
    stashCapacity: Int,
    stashOverflowStrategy: StashOverflowStrategy,
    logOnStashing: Boolean,
    recoveryTimeout: FiniteDuration,
    durableStateStorePluginId: String,
    useContextLoggerForInternalLogging: Boolean) {

  require(
    durableStateStorePluginId != null,
    "DurableStateBehavior plugin id must not be null; use empty string for 'default' state store")
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] sealed trait StashOverflowStrategy

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object StashOverflowStrategy {
  case object Drop extends StashOverflowStrategy
  case object Fail extends StashOverflowStrategy
}
