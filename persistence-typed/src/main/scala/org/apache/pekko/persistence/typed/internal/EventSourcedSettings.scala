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

package org.apache.pekko.persistence.typed.internal

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.persistence.Persistence

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object EventSourcedSettings {

  def apply(
      system: ActorSystem[_],
      journalPluginId: String,
      snapshotPluginId: String
  ): EventSourcedSettings =
    apply(system.settings.config, journalPluginId, snapshotPluginId, None, None)

  def apply(
      system: ActorSystem[_],
      journalPluginId: String,
      snapshotPluginId: String,
      journalPluginConfig: Option[Config],
      snapshotPluginConfig: Option[Config]
  ): EventSourcedSettings =
    apply(system.settings.config, journalPluginId, snapshotPluginId, journalPluginConfig, snapshotPluginConfig)

  def apply(
      config: Config,
      journalPluginId: String,
      snapshotPluginId: String,
      journalPluginConfig: Option[Config],
      snapshotPluginConfig: Option[Config]
  ): EventSourcedSettings = {
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

    val useContextLoggerForInternalLogging = typedConfig.getBoolean("use-context-logger-for-internal-logging")

    Persistence.verifyPluginConfigExists(config, snapshotPluginId, "Snapshot store")

    EventSourcedSettings(
      stashCapacity = stashCapacity,
      stashOverflowStrategy,
      logOnStashing = logOnStashing,
      journalPluginId,
      snapshotPluginId,
      journalPluginConfig,
      snapshotPluginConfig,
      useContextLoggerForInternalLogging)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class EventSourcedSettings(
    stashCapacity: Int,
    stashOverflowStrategy: StashOverflowStrategy,
    logOnStashing: Boolean,
    journalPluginId: String,
    snapshotPluginId: String,
    journalPluginConfig: Option[Config],
    snapshotPluginConfig: Option[Config],
    useContextLoggerForInternalLogging: Boolean) {

  require(journalPluginId != null, "journal plugin id must not be null; use empty string for 'default' journal")
  require(
    snapshotPluginId != null,
    "snapshot plugin id must not be null; use empty string for 'default' snapshot store")

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
