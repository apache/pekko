/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.typed.scaladsl

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.persistence.query.scaladsl.ReadJournal

/**
 * [[EventsBySliceQuery]] that is using a timestamp based offset should also implement this query.
 *
 * API May Change
 */
@ApiMayChange
trait EventTimestampQuery extends ReadJournal {

  def timestampOf(persistenceId: String, sequenceNr: Long): Future[Option[Instant]]

}
