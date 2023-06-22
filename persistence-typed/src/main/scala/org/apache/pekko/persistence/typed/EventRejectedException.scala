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

package org.apache.pekko.persistence.typed

/**
 * Thrown if a journal rejects an event e.g. due to a serialization error.
 */
final class EventRejectedException(persistenceId: PersistenceId, sequenceNr: Long, cause: Throwable)
    extends RuntimeException(s"Rejected event, persistenceId [${persistenceId.id}], sequenceNr [$sequenceNr]", cause)
