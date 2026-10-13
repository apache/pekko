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
trait CurrentEventsByPersistenceIdTypedQuery extends ReadJournal {

  /**
   * Same as [[EventsByPersistenceIdTypedQuery.eventsByPersistenceIdTyped]] but the stream is completed
   * immediately when it reaches the end of the "current" events. Events that are stored after the
   * query is started are not included in the stream.
   */
  def currentEventsByPersistenceIdTyped[Event](
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long): Source[EventEnvelope[Event], NotUsed]

}
