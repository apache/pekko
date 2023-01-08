/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

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
