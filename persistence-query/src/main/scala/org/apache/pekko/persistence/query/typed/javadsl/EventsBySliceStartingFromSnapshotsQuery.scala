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
import pekko.japi.Pair
import pekko.persistence.query.Offset
import pekko.persistence.query.javadsl.ReadJournal
import pekko.persistence.query.typed.EventEnvelope
import pekko.stream.javadsl.Source

/**
 * A plugin may optionally support this query by implementing this trait.
 *
 * `EventsBySliceQuery` that is using a timestamp based offset should also implement [[EventTimestampQuery]] and
 * [[LoadEventQuery]].
 *
 * See also [[EventsBySliceFirehoseQuery]].
 *
 * API May Change
 */
@ApiMayChange
trait EventsBySliceStartingFromSnapshotsQuery extends ReadJournal {

  /**
   * Same as [[EventsBySliceQuery]] but with the purpose to use snapshots as starting points and thereby reducing number of
   * events that have to be loaded. This can be useful if the consumer start from zero without any previously processed
   * offset or if it has been disconnected for a long while and its offset is far behind.
   */
  def eventsBySlicesStartingFromSnapshots[Snapshot, Event](
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset,
      transformSnapshot: java.util.function.Function[Snapshot, Event]): Source[EventEnvelope[Event], NotUsed]

  def sliceForPersistenceId(persistenceId: String): Int

  def sliceRanges(numberOfRanges: Int): java.util.List[Pair[Integer, Integer]]

}
