/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence.testkit.query.internal

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.PersistentRepr
import pekko.persistence.query.Offset
import pekko.persistence.query.TimestampOffset

/**
 * INTERNAL API
 *
 * The test kit journal assigns a strictly increasing timestamp to each atomic write,
 * and an atomic write only contains events of a single persistence id, so
 * `(timestamp, persistenceId, sequenceNr)` identifies the position of an event.
 */
@InternalApi
private[pekko] object TimestampOffsets {

  def offsetFor(pr: PersistentRepr): TimestampOffset = {
    val timestamp = Instant.ofEpochMilli(pr.timestamp)
    val readTimestamp = Instant.now().truncatedTo(ChronoUnit.MICROS)
    TimestampOffset(timestamp, readTimestamp, Map(pr.persistenceId -> pr.sequenceNr))
  }

  /**
   * Returns a predicate that selects the events after the given offset, which must be
   * a [[TimestampOffset]] or [[pekko.persistence.query.NoOffset]]. The offset is exclusive.
   */
  def isAfter(offset: Offset): PersistentRepr => Boolean = {
    val timestampOffset = TimestampOffset.toTimestampOffset(offset)
    pr => {
      val cmp = Instant.ofEpochMilli(pr.timestamp).compareTo(timestampOffset.timestamp)
      cmp > 0 || (cmp == 0 && timestampOffset.seen.get(pr.persistenceId).forall(pr.sequenceNr > _))
    }
  }
}
