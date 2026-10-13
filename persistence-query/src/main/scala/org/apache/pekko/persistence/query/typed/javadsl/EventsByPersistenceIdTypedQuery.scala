/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.typed.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.ApiMayChange
import pekko.persistence.query.javadsl.ReadJournal
import pekko.persistence.query.typed.EventEnvelope
import pekko.stream.javadsl.Source

/**
 * A plugin may optionally support this query by implementing this trait.
 *
 * API May Change
 */
@ApiMayChange
trait EventsByPersistenceIdTypedQuery extends ReadJournal {

  /**
   * Query events for a specific `PersistenceId`.
   *
   * Events are emitted in the order they were stored. The stream also emits events that are persisted
   * after the query is started. The stream is not completed when it reaches the end of the currently stored
   * events, but it continues to push new events when new events are persisted. Corresponding query that is
   * completed when it reaches the end of the currently stored events is provided by
   * [[CurrentEventsByPersistenceIdTypedQuery.currentEventsByPersistenceIdTyped]].
   *
   * The `fromSequenceNr` and `toSequenceNr` can be used to limit what sequence numbers the returned stream
   * will contain. Both sides are inclusive. `0` and `Long.MaxValue` are used to signify no lower/upper bound.
   */
  def eventsByPersistenceIdTyped[Event](
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long): Source[EventEnvelope[Event], NotUsed]

}
