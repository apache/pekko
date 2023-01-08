/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.javadsl

import java.time.Duration
import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._

import org.apache.pekko
import pekko.Done
import pekko.actor.ClassicActorSystemProvider
import pekko.persistence.testkit.scaladsl
import pekko.util.JavaDurationConverters._

/**
 * Test utility to initialize persistence plugins. Useful when initialization order or coordination
 * is needed. For example to avoid creating tables concurrently.
 */
object PersistenceInit {

  /**
   * Initialize the default journal and snapshot plugins.
   *
   * @return a `CompletionStage` that is completed when the initialization has completed
   */
  def initializeDefaultPlugins(system: ClassicActorSystemProvider, timeout: Duration): CompletionStage[Done] =
    initializePlugins(system, journalPluginId = "", snapshotPluginId = "", timeout)

  /**
   * Initialize the given journal and snapshot plugins.
   *
   * The `snapshotPluginId` can be empty (`""`) if snapshot plugin isn't used.
   *
   * @return a `CompletionStage` that is completed when the initialization has completed
   */
  def initializePlugins(
      system: ClassicActorSystemProvider,
      journalPluginId: String,
      snapshotPluginId: String,
      timeout: Duration): CompletionStage[Done] =
    scaladsl.PersistenceInit.initializePlugins(system, journalPluginId, snapshotPluginId, timeout.asScala).toJava

}
