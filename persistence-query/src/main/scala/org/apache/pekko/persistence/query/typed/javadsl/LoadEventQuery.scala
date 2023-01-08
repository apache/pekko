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

package org.apache.pekko.persistence.query.typed.javadsl

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.persistence.query.javadsl.ReadJournal
import pekko.persistence.query.typed.EventEnvelope

/**
 * [[EventsBySliceQuery]] that is using a timestamp based offset should also implement this query.
 *
 * API May Change
 */
@ApiMayChange
trait LoadEventQuery extends ReadJournal {

  /**
   * Load a single event on demand. The `CompletionStage` is completed with a `NoSuchElementException` if
   * the event for the given `persistenceId` and `sequenceNr` doesn't exist.
   */
  def loadEnvelope[Event](persistenceId: String, sequenceNr: Long): CompletionStage[EventEnvelope[Event]]
}
