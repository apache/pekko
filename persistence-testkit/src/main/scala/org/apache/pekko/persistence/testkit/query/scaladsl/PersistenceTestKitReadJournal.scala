/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.query.scaladsl
import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ExtendedActorSystem
import pekko.persistence.journal.Tagged
import pekko.persistence.query.NoOffset
import pekko.persistence.query.Offset
import pekko.persistence.query.scaladsl.{
  CurrentEventsByPersistenceIdQuery,
  CurrentEventsByTagQuery,
  EventsByPersistenceIdQuery,
  PagedPersistenceIdsQuery,
  ReadJournal
}
import pekko.persistence.query.{ EventEnvelope, Sequence }
import pekko.persistence.testkit.EventStorage
import pekko.persistence.testkit.internal.InMemStorageExtension
import pekko.persistence.testkit.query.internal.EventsByPersistenceIdStage
import pekko.stream.scaladsl.Source
import pekko.util.unused
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import pekko.persistence.Persistence
import pekko.persistence.query.typed
import pekko.persistence.query.typed.scaladsl.CurrentEventsBySliceQuery
import pekko.persistence.typed.PersistenceId
import pekko.persistence.query.scaladsl.EventsByTagQuery
import pekko.persistence.testkit.query.internal.EventsByTagStage

import scala.collection.immutable

object PersistenceTestKitReadJournal {
  val Identifier = "pekko.persistence.testkit.query"
}

final class PersistenceTestKitReadJournal(system: ExtendedActorSystem, @unused config: Config, configPath: String)
    extends ReadJournal
    with EventsByPersistenceIdQuery
    with CurrentEventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with CurrentEventsBySliceQuery
    with PagedPersistenceIdsQuery
    with EventsByTagQuery {

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

  override def currentEventsByTag(tag: String, offset: Offset = NoOffset): Source[EventEnvelope, NotUsed] = {
    offset match {
      case NoOffset =>
      case _ =>
        throw new UnsupportedOperationException("Offsets not supported for persistence test kit currentEventsByTag yet")
    }
    Source(storage.tryReadByTag(tag)).map { pr =>
      EventEnvelope(
        Sequence(pr.sequenceNr),
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
    offset match {
      case NoOffset =>
      case _ =>
        throw new UnsupportedOperationException("Offsets not supported for persistence test kit currentEventsByTag yet")
    }
    val prs = storage.tryRead(entityType,
      repr => {
        val pid = repr.persistenceId
        val slice = persistence.sliceForPersistenceId(pid)
        PersistenceId.extractEntityType(pid) == entityType && slice >= minSlice && slice <= maxSlice
      })
    Source(prs).map { pr =>
      val slice = persistence.sliceForPersistenceId(pr.persistenceId)
      new typed.EventEnvelope[Event](
        Sequence(pr.sequenceNr),
        pr.persistenceId,
        pr.sequenceNr,
        Some(pr.payload.asInstanceOf[Event]),
        pr.timestamp,
        pr.metadata,
        entityType,
        slice)
    }
  }

  override def sliceForPersistenceId(persistenceId: String): Int =
    persistence.sliceForPersistenceId(persistenceId)

  override def sliceRanges(numberOfRanges: Int): immutable.Seq[Range] =
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
    if (offset != NoOffset) {
      throw new UnsupportedOperationException("Offsets not supported for persistence test kit currentEventsByTag yet")
    }
    Source.fromGraph(new EventsByTagStage(tag, storage))
  }
}
