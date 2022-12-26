/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.persistence.query.EventEnvelope
import pekko.stream.javadsl.Source

/**
 * A plugin may optionally support this query by implementing this interface.
 */
trait CurrentEventsByPersistenceIdQuery extends ReadJournal {

  /**
   * Same type of query as [[EventsByPersistenceIdQuery#eventsByPersistenceId]]
   * but the event stream is completed immediately when it reaches the end of
   * the "result set". Events that are stored after the query is completed are
   * not included in the event stream.
   */
  def currentEventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long): Source[EventEnvelope, NotUsed]

}
