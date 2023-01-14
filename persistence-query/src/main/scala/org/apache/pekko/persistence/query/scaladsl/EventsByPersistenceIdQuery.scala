/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.scaladsl

import org.apache.pekko
import pekko.NotUsed
import pekko.persistence.query.EventEnvelope
import pekko.stream.scaladsl.Source

/**
 * A plugin may optionally support this query by implementing this trait.
 */
trait EventsByPersistenceIdQuery extends ReadJournal {

  /**
   * Query events for a specific `PersistentActor` identified by `persistenceId`.
   *
   * You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr`
   * or use `0L` and `Long.MaxValue` respectively to retrieve all events. The query will
   * return all the events inclusive of the `fromSequenceNr` and `toSequenceNr` values.
   *
   * The returned event stream should be ordered by sequence number.
   *
   * The stream is not completed when it reaches the end of the currently stored events,
   * but it continues to push new events when new events are persisted.
   * Corresponding query that is completed when it reaches the end of the currently
   * stored events is provided by [[CurrentEventsByPersistenceIdQuery#currentEventsByPersistenceId]].
   */
  def eventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long): Source[EventEnvelope, NotUsed]

}
