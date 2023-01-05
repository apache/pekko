/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization.jackson

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.serialization.Serialization

/**
 * INTERNAL API
 */
@InternalApi private[pekko] trait ActorSystemAccess {
  def currentSystem(): ExtendedActorSystem = {
    Serialization.currentTransportInformation.value match {
      case null =>
        throw new IllegalStateException(
          "Can't access current ActorSystem, Serialization.currentTransportInformation was not set.")
      case Serialization.Information(_, system) => system.asInstanceOf[ExtendedActorSystem]
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ActorSystemAccess extends ActorSystemAccess
