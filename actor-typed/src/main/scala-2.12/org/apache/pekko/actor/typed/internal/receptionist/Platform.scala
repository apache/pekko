/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.receptionist

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[receptionist] object Platform {
  type Service[K <: AbstractServiceKey] = ActorRef[K#Protocol]
  type Subscriber[K <: AbstractServiceKey] = ActorRef[ReceptionistMessages.Listing[K#Protocol]]
}
