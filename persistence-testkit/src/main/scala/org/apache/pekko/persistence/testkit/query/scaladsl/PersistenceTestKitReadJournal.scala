/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.query.scaladsl

import scala.annotation.nowarn

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ExtendedActorSystem
import pekko.persistence.Persistence
import pekko.persistence.journal.Tagged
import pekko.persistence.query.{ EventEnvelope, Sequence }
import pekko.persistence.query.NoOffset
import pekko.persistence.query.Offset
import pekko.persistence.query.TimestampOffset
import pekko.persistence.query.scaladsl.{
  CurrentEventsByPersistenceIdQuery,
  CurrentEventsByTagQuery,
  EventsByPersistenceIdQuery,
  PagedPersistenceIdsQuery,
  ReadJournal
}
import pekko.persistence.query.scaladsl.EventsByTagQuery
import pekko.persistence.query.typed
import pekko.persistence.query.typed.scaladsl.CurrentEventsByPersistenceIdTypedQuery
import pekko.persistence.query.typed.scaladsl.CurrentEventsBySliceQuery
import pekko.persistence.query.typed.scaladsl.EventsByPersistenceIdTypedQuery
import pekko.persistence.query.typed.scaladsl.EventsBySliceQuery
import pekko.persistence.testkit.EventStorage
import pekko.persistence.testkit.internal.InMemStorageExtension
import pekko.persistence.testkit.query.internal.EventsByPersistenceIdStage
import pekko.persistence.testkit.query.internal.EventsBySliceStage
import pekko.persistence.testkit.query.internal.EventsByTagStage
import pekko.persistence.testkit.query.internal.TimestampOffsets
import pekko.persistence.testkit.query.internal.TypedEventsByPersistenceIdStage
import pekko.persistence.typed.PersistenceId
import pekko.stream.scaladsl.Source

import org.slf4j.LoggerFactory

import com.typesafe.config.Config

object PersistenceTestKitReadJournal {
  val Identifier = "pekko.persistence.testkit.query"
}

final class PersistenceTestKitReadJournal(system: ExtendedActorSystem, @nowarn("msg=never used") config: Config,
    configPath: String)
    extends ReadJournal
    with EventsByPersistenceIdQuery
    with CurrentEventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with CurrentEventsBySliceQuery
    with PagedPersistenceIdsQuery
    with EventsByTagQuery
    with EventsBySliceQuery
    with EventsByPersistenceIdTypedQuery
    with CurrentEventsByPersistenceIdTypedQuery {

  private val log = LoggerFactory.getLogger(getClass)

  private val storage: EventStorage = {
    // use shared path up to before `query` to identify which inmem journal we are addressing
    val storagePluginId = configPath.replaceAll("""query$""", "journal")
    log.debug("Using in memory storage [{}] for test kit read journal", storagePluginId)
    InMemStorageExtension(system).storageFor(storagePluginId)
  }

  private val persistence = Persistence(system)

  private def unwrapTaggedPayload(payload: Any): Any = payload match {
    case Tagged(payload, _) => payload
    case payload            => payload
  }

  private def tagsFor(payload: Any): Set[String] = payload match {
    case Tagged(_, tags) => tags
    case _               => Set.empty
  }

  override def eventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long = 0,
      toSequenceNr: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
    Source.fromGraph(new EventsByPersistenceIdStage(persistenceId, fromSequenceNr, toSequenceNr, storage))
  }

  override def currentEventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long = 0,
      toSequenceNr: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
    Source(storage.tryRead(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue)).map { pr =>
      EventEnvelope(
        Sequence(pr.sequenceNr),
        persistenceId,
        pr.sequenceNr,
        unwrapTaggedPayload(pr.payload),
        pr.timestamp,
        pr.metadata)
    }
  }

  override def eventsByPersistenceIdTyped[Event](
      persistenceId: String,
      fromSequenceNr: Long = 0,
      toSequenceNr: Long = Long.MaxValue): Source[typed.EventEnvelope[Event], NotUsed] = {
    Source.fromGraph(
      new TypedEventsByPersistenceIdStage[Event](persistenceId, fromSequenceNr, toSequenceNr, storage, persistence))
  }

  override def currentEventsByPersistenceIdTyped[Event](
      persistenceId: String,
      fromSequenceNr: Long = 0,
      toSequenceNr: Long = Long.MaxValue): Source[typed.EventEnvelope[Event], NotUsed] = {
    val slice = persistence.sliceForPersistenceId(persistenceId)
    val entityType = PersistenceId.extractEntityType(persistenceId)
    Source(storage.tryRead(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue)).map { pr =>
      typed.EventEnvelope(
        TimestampOffsets.offsetFor(pr),
        persistenceId,
        pr.sequenceNr,
        unwrapTaggedPayload(pr.payload).asInstanceOf[Event],
        pr.timestamp,
        entityType,
        slice,
        filtered = false,
        source = "",
        tags = tagsFor(pr.payload))
    }
  }

  override def currentEventsByTag(tag: String, offset: Offset = NoOffset): Source[EventEnvelope, NotUsed] = {
    val isAfterOffset = TimestampOffsets.isAfter(offset)
    Source(storage.tryReadByTag(tag).filter(isAfterOffset)).map { pr =>
      EventEnvelope(
        TimestampOffsets.offsetFor(pr),
        pr.persistenceId,
        pr.sequenceNr,
        unwrapTaggedPayload(pr.payload),
        pr.timestamp,
        pr.metadata)
    }
  }

  override def currentEventsBySlices[Event](
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset): Source[typed.EventEnvelope[Event], NotUsed] = {
    val isAfterOffset = TimestampOffsets.isAfter(offset)
    val prs = storage.tryRead(entityType,
      repr => {
        val pid = repr.persistenceId
        val slice = persistence.sliceForPersistenceId(pid)
        PersistenceId.extractEntityType(pid) == entityType && slice >= minSlice && slice <= maxSlice &&
        isAfterOffset(repr)
      })
    Source(prs).map { pr =>
      val slice = persistence.sliceForPersistenceId(pr.persistenceId)
      typed.EventEnvelope(
        TimestampOffsets.offsetFor(pr),
        pr.persistenceId,
        pr.sequenceNr,
        unwrapTaggedPayload(pr.payload).asInstanceOf[Event],
        pr.timestamp,
        entityType,
        slice,
        filtered = false,
        source = "",
        tags = tagsFor(pr.payload))
    }
  }

  override def sliceForPersistenceId(persistenceId: String): Int =
    persistence.sliceForPersistenceId(persistenceId)

  override def sliceRanges(numberOfRanges: Int): Seq[Range] =
    persistence.sliceRanges(numberOfRanges)

  /**
   * Get the current persistence ids.
   *
   * Not all plugins may support in database paging, and may simply use drop/take Pekko streams operators
   * to manipulate the result set according to the paging parameters.
   *
   * @param afterId The ID to start returning results from, or [[scala.None]] to return all ids. This should be an id
   *                returned from a previous invocation of this command. Callers should not assume that ids are
   *                returned in sorted order.
   * @param limit   The maximum results to return. Use Long.MaxValue to return all results. Must be greater than zero.
   * @return A source containing all the persistence ids, limited as specified.
   */
  override def currentPersistenceIds(afterId: Option[String], limit: Long): Source[String, NotUsed] =
    storage.currentPersistenceIds(afterId, limit)

  override def eventsByTag(tag: String, offset: Offset = NoOffset): Source[EventEnvelope, NotUsed] = {
    // validate eagerly so that an unsupported offset type fails the call rather than the stream
    TimestampOffset.toTimestampOffset(offset)
    Source.fromGraph(new EventsByTagStage(tag, offset, storage))
  }

  override def eventsBySlices[Event](
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset
  ): Source[typed.EventEnvelope[Event], NotUsed] = {
    // validate eagerly so that an unsupported offset type fails the call rather than the stream
    TimestampOffset.toTimestampOffset(offset)
    Source.fromGraph(new EventsBySliceStage(entityType, minSlice, maxSlice, offset, storage, persistence))
  }
}
