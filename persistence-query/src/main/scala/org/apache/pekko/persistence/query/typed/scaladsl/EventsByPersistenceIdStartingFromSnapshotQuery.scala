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

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.ApiMayChange
import pekko.persistence.query.scaladsl.ReadJournal
import pekko.persistence.query.typed.EventEnvelope
import pekko.stream.scaladsl.Source

/**
 * A plugin may optionally support this query by implementing this trait.
 */
@ApiMayChange
trait EventsByPersistenceIdStartingFromSnapshotQuery extends ReadJournal {

  /**
   * Same as [[EventsByPersistenceIdTypedQuery]] but with the purpose to use snapshot as starting point
   * and thereby reducing number of events that have to be loaded.
   */
  def eventsByPersistenceIdStartingFromSnapshot[Snapshot, Event](
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      transformSnapshot: Snapshot => Event): Source[EventEnvelope[Event], NotUsed]

}
