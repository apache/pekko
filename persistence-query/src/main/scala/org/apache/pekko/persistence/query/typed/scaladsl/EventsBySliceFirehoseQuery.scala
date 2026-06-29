/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.typed.scaladsl

import java.time.Instant

import scala.annotation.nowarn
import scala.collection.immutable
import scala.concurrent.Future

import com.typesafe.config.Config

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ExtendedActorSystem
import pekko.persistence.Persistence
import pekko.persistence.query.Offset
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.scaladsl._
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.query.typed.internal.EventsBySliceFirehose
import pekko.stream.scaladsl.Source

object EventsBySliceFirehoseQuery {
  val Identifier = "pekko.persistence.query.events-by-slice-firehose"

}

/**
 * This wrapper of [[EventsBySliceQuery]] gives better scalability when many consumers retrieve the
 * same events, for example many Projections of the same entity type. The purpose is to share
 * the stream of events from the database and fan out to connected consumer streams. Thereby fewer
 * queries and loading of events from the database.
 *
 * It is retrieved with:
 * {{{
 * val queries = PersistenceQuery(system).readJournalFor[EventsBySliceQuery](EventsBySliceFirehoseQuery.Identifier)
 * }}}
 *
 * Corresponding Java API is in [[org.apache.pekko.persistence.query.typed.javadsl.EventsBySliceFirehoseQuery]].
 *
 * Configuration settings can be defined in the configuration section with the
 * absolute path corresponding to the identifier, which is `"pekko.persistence.query.events-by-slice-firehose"`
 * for the default [[EventsBySliceFirehoseQuery#Identifier]]. See `reference.conf`.
 */
@nowarn("msg=never used")
final class EventsBySliceFirehoseQuery(system: ExtendedActorSystem, config: Config, cfgPath: String)
    extends ReadJournal
    with EventsBySliceQuery
    with EventsBySliceStartingFromSnapshotsQuery
    with EventTimestampQuery
    with LoadEventQuery {

  private lazy val persistenceExt = Persistence(system)
  private lazy val settings = EventsBySliceFirehose.Settings(system, cfgPath)

  override def eventsBySlices[Event](
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset): Source[EventEnvelope[Event], NotUsed] =
    EventsBySliceFirehose(system).eventsBySlices(cfgPath, entityType, minSlice, maxSlice, offset)

  override def eventsBySlicesStartingFromSnapshots[Snapshot, Event](
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset,
      transformSnapshot: Snapshot => Event): Source[EventEnvelope[Event], NotUsed] =
    EventsBySliceFirehose(system).eventsBySlicesStartingFromSnapshots(
      cfgPath,
      entityType,
      minSlice,
      maxSlice,
      offset,
      transformSnapshot)

  override def sliceForPersistenceId(persistenceId: String): Int =
    persistenceExt.sliceForPersistenceId(persistenceId)

  override def sliceRanges(numberOfRanges: Int): immutable.Seq[Range] =
    persistenceExt.sliceRanges(numberOfRanges)

  override def timestampOf(persistenceId: String, sequenceNr: Long): Future[Option[Instant]] = {
    eventsBySliceQuery match {
      case q: EventTimestampQuery => q.timestampOf(persistenceId, sequenceNr)
      case _                      =>
        throw new IllegalArgumentException(
          s"Underlying ReadJournal [${settings.delegateQueryPluginId}] doesn't implement EventTimestampQuery")
    }
  }

  override def loadEnvelope[Event](persistenceId: String, sequenceNr: Long): Future[EventEnvelope[Event]] =
    eventsBySliceQuery match {
      case q: LoadEventQuery => q.loadEnvelope(persistenceId, sequenceNr)
      case _                 =>
        throw new IllegalArgumentException(
          s"Underlying ReadJournal [${settings.delegateQueryPluginId}] " +
          "doesn't implement LoadEventQuery")
    }

  private def eventsBySliceQuery: EventsBySliceQuery = {
    val delegateQueryPluginId =
      EventsBySliceFirehose.Settings.delegateQueryPluginId(system.settings.config.getConfig(cfgPath))
    PersistenceQuery(system).readJournalFor[EventsBySliceQuery](delegateQueryPluginId)
  }

}
