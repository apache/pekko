/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.typed.scaladsl

import scala.collection.immutable

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.ApiMayChange
import pekko.persistence.query.Offset
import pekko.persistence.query.scaladsl.ReadJournal
import pekko.persistence.query.typed.EventEnvelope
import pekko.stream.scaladsl.Source

/**
 * A plugin may optionally support this query by implementing this trait.
 *
 * API May Change
 */
@ApiMayChange
trait CurrentEventsBySliceQuery extends ReadJournal {

  /**
   * Same type of query as [[EventsBySliceQuery.eventsBySlices]] but the event stream is completed immediately when it
   * reaches the end of the "result set". Depending on journal implementation, this may mean all events up to when the
   * query is started, or it may include events that are persisted while the query is still streaming results. For
   * eventually consistent stores, it may only include all events up to some point before the query is started.
   */
  def currentEventsBySlices[Event](
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset): Source[EventEnvelope[Event], NotUsed]

  def sliceForPersistenceId(persistenceId: String): Int

  def sliceRanges(numberOfRanges: Int): immutable.Seq[Range]
}
