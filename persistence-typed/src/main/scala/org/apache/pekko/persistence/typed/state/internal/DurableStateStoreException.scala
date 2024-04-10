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

package org.apache.pekko.persistence.typed.state.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.typed.PersistenceId

/**
 * INTERNAL API
 *
 * Base class for exceptions thrown by the Durable State implementations.
 * Extensible since v1.1.0.
 */
@InternalApi
private[pekko] class DurableStateStoreException(msg: String, cause: Throwable)
    extends RuntimeException(msg, cause) {
  def this(persistenceId: PersistenceId, sequenceNr: Long, cause: Throwable) =
    this(s"Failed to persist state with sequence number [$sequenceNr] for persistenceId [${persistenceId.id}]", cause)

  def this(msg: String) = this(msg, null)
}
