/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.query.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.japi.Pair
import pekko.persistence.query.EventEnvelope
import pekko.persistence.query.Offset
import pekko.persistence.query.javadsl.{
  CurrentEventsByPersistenceIdQuery,
  CurrentEventsByTagQuery,
  EventsByPersistenceIdQuery,
  ReadJournal
}
import pekko.persistence.query.typed
import pekko.persistence.query.typed.javadsl.CurrentEventsBySliceQuery
import pekko.stream.javadsl.Source
import pekko.persistence.testkit.query.scaladsl

object PersistenceTestKitReadJournal {
  val Identifier = "akka.persistence.testkit.query"
}

final class PersistenceTestKitReadJournal(delegate: scaladsl.PersistenceTestKitReadJournal)
    extends ReadJournal
    with EventsByPersistenceIdQuery
    with CurrentEventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with CurrentEventsBySliceQuery {

  override def eventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    delegate.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def currentEventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    delegate.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    delegate.currentEventsByTag(tag, offset).asJava

  override def currentEventsBySlices[Event](
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset): Source[typed.EventEnvelope[Event], NotUsed] =
    delegate.currentEventsBySlices(entityType, minSlice, maxSlice, offset).asJava

  override def sliceForPersistenceId(persistenceId: String): Int =
    delegate.sliceForPersistenceId(persistenceId)

  override def sliceRanges(numberOfRanges: Int): java.util.List[Pair[Integer, Integer]] = {
    import pekko.util.ccompat.JavaConverters._
    delegate
      .sliceRanges(numberOfRanges)
      .map(range => Pair(Integer.valueOf(range.min), Integer.valueOf(range.max)))
      .asJava
  }
}
