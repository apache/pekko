/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.typed.javadsl

import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.persistence.query.javadsl.ReadJournal

/**
 * [[EventsBySliceQuery]] that is using a timestamp based offset should also implement this query.
 *
 * API May Change
 */
@ApiMayChange
trait EventTimestampQuery extends ReadJournal {

  def timestampOf(persistenceId: String, sequenceNr: Long): CompletionStage[Optional[Instant]]

}
