/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import org.apache.pekko.actor.ActorRef

/**
 * Used as failure exception by an `ask` operator if the target actor terminates.
 * See `Flow.ask` and `Flow.watch`.
 */
final class WatchedActorTerminatedException(val watchingStageName: String, val ref: ActorRef)
    extends RuntimeException(s"Actor watched by [$watchingStageName] has terminated! Was: $ref")
