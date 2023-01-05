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
  type Aux[P] = AbstractServiceKey { type Protocol = P }

  type SubscriptionsKV[K <: Aux[_]] = K match {
    case Aux[t] => ActorRef[ReceptionistMessages.Listing[t]]
  }
}
