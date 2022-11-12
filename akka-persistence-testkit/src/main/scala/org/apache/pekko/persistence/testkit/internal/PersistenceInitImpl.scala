/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.internal

import java.util.concurrent.TimeUnit

import org.apache.pekko
import pekko.actor.ActorLogging
import pekko.actor.Props
import pekko.annotation.InternalApi
import pekko.persistence.PersistentActor
import pekko.persistence.RecoveryCompleted

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object PersistenceInitImpl {

  def props(journalPluginId: String, snapshotPluginId: String, persistenceId: String): Props = {
    Props(new PersistenceInitImpl(journalPluginId, snapshotPluginId, persistenceId))
  }
}

/**
 * INTERNAL API: Initialize a journal and snapshot plugin by starting this `PersistentActor`
 * and send any message to it. It will reply to the `sender()` with the same message when
 * recovery has completed.
 */
@InternalApi private[pekko] class PersistenceInitImpl(
    override val journalPluginId: String,
    override val snapshotPluginId: String,
    override val persistenceId: String)
    extends PersistentActor
    with ActorLogging {

  private val startTime = System.nanoTime()

  def receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.debug(
        "Initialization completed for journal [{}] and snapshot [{}] plugins, with persistenceId [{}], in [{} ms]",
        journalPluginId,
        snapshotPluginId,
        persistenceId,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime))
    case _ =>
  }

  def receiveCommand: Receive = {
    case msg =>
      // recovery has completed
      sender() ! msg
      context.stop(self)
  }
}
