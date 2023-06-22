/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.typed.PersistenceId

/**
 * INTERNAL API
 *
 * Used for journal failures. Private to pekko as only internal supervision strategies should use it.
 */
@InternalApi
final private[pekko] class JournalFailureException(msg: String, cause: Throwable) extends RuntimeException(msg, cause) {
  def this(persistenceId: PersistenceId, sequenceNr: Long, eventType: String, cause: Throwable) =
    this(
      s"Failed to persist event type [$eventType] with sequence number [$sequenceNr] for persistenceId [${persistenceId.id}]",
      cause)
}
