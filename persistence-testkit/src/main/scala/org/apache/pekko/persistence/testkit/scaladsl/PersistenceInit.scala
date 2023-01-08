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

package org.apache.pekko.persistence.testkit.scaladsl

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.Done
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.persistence.testkit.internal.PersistenceInitImpl
import pekko.util.Timeout

/**
 * Test utility to initialize persistence plugins. Useful when initialization order or coordination
 * is needed. For example to avoid creating tables concurrently.
 */
object PersistenceInit {

  /**
   * Initialize the default journal and snapshot plugins.
   *
   * @return a `Future` that is completed when the initialization has completed
   */
  def initializeDefaultPlugins(system: ClassicActorSystemProvider, timeout: FiniteDuration): Future[Done] =
    initializePlugins(system, journalPluginId = "", snapshotPluginId = "", timeout)

  /**
   * Initialize the given journal and snapshot plugins.
   *
   * The `snapshotPluginId` can be empty (`""`) if snapshot plugin isn't used.
   *
   * @return a `Future` that is completed when the initialization has completed
   */
  def initializePlugins(
      system: ClassicActorSystemProvider,
      journalPluginId: String,
      snapshotPluginId: String,
      timeout: FiniteDuration): Future[Done] = {
    val persistenceId: String = s"persistenceInit-${UUID.randomUUID()}"
    val extSystem = system.classicSystem.asInstanceOf[ExtendedActorSystem]
    val ref =
      extSystem.systemActorOf(
        PersistenceInitImpl.props(journalPluginId, snapshotPluginId, persistenceId),
        persistenceId)
    import extSystem.dispatcher

    import pekko.pattern.ask
    implicit val askTimeout: Timeout = timeout
    (ref ? "start").map(_ => Done)
  }
}
