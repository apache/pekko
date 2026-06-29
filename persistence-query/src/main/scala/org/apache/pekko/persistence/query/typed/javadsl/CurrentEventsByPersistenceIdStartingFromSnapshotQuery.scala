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

package org.apache.pekko.persistence.query.typed.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.ApiMayChange
import pekko.persistence.query.javadsl.ReadJournal
import pekko.persistence.query.typed.EventEnvelope
import pekko.stream.javadsl.Source

/**
 * A plugin may optionally support this query by implementing this trait.
 */
@ApiMayChange
trait CurrentEventsByPersistenceIdStartingFromSnapshotQuery extends ReadJournal {

  /**
   * Same as [[CurrentEventsByPersistenceIdTypedQuery]] but with the purpose to use snapshot as starting point
   * and thereby reducing number of events that have to be loaded.
   */
  def currentEventsByPersistenceIdStartingFromSnapshot[Snapshot, Event](
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      transformSnapshot: java.util.function.Function[Snapshot, Event]): Source[EventEnvelope[Event], NotUsed]

}
