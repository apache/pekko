/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit

import org.apache.pekko
import pekko.actor.ActorLogging
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

import com.typesafe.config.{ Config, ConfigFactory }

import pekko.annotation.InternalApi
import pekko.persistence._
import pekko.persistence.journal.AsyncWriteJournal
import pekko.persistence.journal.Tagged
import pekko.persistence.snapshot.SnapshotStore
import pekko.persistence.testkit.internal.CurrentTime
import pekko.persistence.testkit.internal.{ InMemStorageExtension, SnapshotStorageEmulatorExtension }
import pekko.util.unused

/**
 * INTERNAL API
 *
 * Persistence testkit plugin for events.
 */
@InternalApi
class PersistenceTestKitPlugin(@unused cfg: Config, cfgPath: String) extends AsyncWriteJournal with ActorLogging {

  private final val storage = {
    log.debug("Using in memory storage [{}] for test kit journal", cfgPath)
    InMemStorageExtension(context.system).storageFor(cfgPath)
  }
  private val eventStream = context.system.eventStream

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    Future.fromTry(Try(messages.map(aw => {
      val timestamp = CurrentTime.now()
      val data = aw.payload.map(pl =>
        pl.payload match {
          case _ => pl.withTimestamp(timestamp)
        })

      val result: Try[Unit] = storage.tryAdd(data)
      result.foreach { _ =>
        messages.foreach { aw =>
          eventStream.publish(PersistenceTestKitPlugin.Write(aw.persistenceId, aw.highestSequenceNr))
        }
      }
      result
    })))
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    Future.fromTry(Try(storage.tryDelete(persistenceId, toSequenceNr)))

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
      recoveryCallback: PersistentRepr => Unit): Future[Unit] =
    Future.fromTry(
      Try(
        storage
          .tryRead(persistenceId, fromSequenceNr, toSequenceNr, max)
          .map { repr =>
            // we keep the tags in the repr, so remove those here
            repr.payload match {
              case Tagged(payload, _) => repr.withPayload(payload)
              case _                  => repr
            }

          }
          .foreach(recoveryCallback)))

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    Future.fromTry(Try {
      val found = storage.tryReadSeqNumber(persistenceId)
      if (found < fromSequenceNr) fromSequenceNr else found
    })

}

object PersistenceTestKitPlugin {

  val PluginId = "pekko.persistence.testkit.journal"

  import pekko.util.ccompat.JavaConverters._

  def getInstance() = this

  val config: Config = ConfigFactory.parseMap(
    Map(
      "pekko.persistence.journal.plugin" -> PluginId,
      s"$PluginId.class" -> s"${classOf[PersistenceTestKitPlugin].getName}").asJava)

  private[testkit] case class Write(persistenceId: String, toSequenceNr: Long)

}

/**
 * INTERNAL API
 *
 * Persistence testkit plugin for snapshots.
 */
@InternalApi
class PersistenceTestKitSnapshotPlugin extends SnapshotStore {

  private final val storage = SnapshotStorageEmulatorExtension(context.system)

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] =
    Future.fromTry(Try(storage.tryRead(persistenceId, criteria)))

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    Future.fromTry(Try(storage.tryAdd(metadata, snapshot)))

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    Future.fromTry(Try(storage.tryDelete(metadata)))

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] =
    Future.successful(Try(storage.tryDelete(persistenceId, criteria)))

}

object PersistenceTestKitSnapshotPlugin {

  val PluginId = "pekko.persistence.testkit.snapshotstore.pluginid"

  import pekko.util.ccompat.JavaConverters._

  def getInstance() = this

  val config: Config = ConfigFactory.parseMap(
    Map(
      "pekko.persistence.snapshot-store.plugin" -> PluginId,
      s"$PluginId.class" -> classOf[PersistenceTestKitSnapshotPlugin].getName,
      s"$PluginId.snapshot-is-optional" -> false // fallback isn't used by the testkit
    ).asJava)

}

object PersistenceTestKitDurableStateStorePlugin {

  val PluginId = "pekko.persistence.testkit.state"

  import pekko.util.ccompat.JavaConverters._

  def getInstance() = this

  val config: Config = ConfigFactory.parseMap(Map("pekko.persistence.state.plugin" -> PluginId).asJava)
}
