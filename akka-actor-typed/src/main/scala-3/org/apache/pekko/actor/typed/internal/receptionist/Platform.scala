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
  type Aux[P] = AbstractServiceKey { type Protocol = P }

  type Service[K <: Aux[_]] = K match {
    case Aux[t] => ActorRef[t]
  }

  type Subscriber[K <: Aux[_]] = K match {
    case Aux[t] => ActorRef[ReceptionistMessages.Listing[t]]
  }
}
