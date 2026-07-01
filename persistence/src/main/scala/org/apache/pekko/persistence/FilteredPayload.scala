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

package org.apache.pekko.persistence

/**
 * In some use cases with projections and events by slice filtered events needs to be stored in the journal
 * to keep the sequence numbers for a given persistence id gap free. This placeholder payload is used for those
 * cases and serialized down to a 0-byte representation when stored in the database.
 *
 * This payload is not in general expected to show up for users but in some scenarios/queries it may.
 *
 * In the typed queries `EventEnvelope` this should be flagged as `filtered` and turned into a non-present payload
 * by the query plugin implementations.
 */
case object FilteredPayload
