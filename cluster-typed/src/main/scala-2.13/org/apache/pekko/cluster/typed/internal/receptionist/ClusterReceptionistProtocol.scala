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

package org.apache.pekko.cluster.typed.internal.receptionist

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.internal.receptionist.{ AbstractServiceKey, ReceptionistMessages }
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[receptionist] object ClusterReceptionistProtocol {
  type SubscriptionsKV[K <: AbstractServiceKey] = ActorRef[ReceptionistMessages.Listing[K#Protocol]]
}
