/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.receptionist

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Dispatchers
import pekko.actor.typed.Props
import pekko.actor.typed.receptionist.Receptionist
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class ReceptionistImpl(system: ActorSystem[_]) extends Receptionist {

  override val ref: ActorRef[Receptionist.Command] = {
    val provider: ReceptionistBehaviorProvider =
      if (system.settings.classicSettings.ProviderSelectionType.hasCluster) {
        system.dynamicAccess
          .getObjectFor[ReceptionistBehaviorProvider](
            "org.apache.pekko.cluster.typed.internal.receptionist.ClusterReceptionist")
          .recover {
            case e =>
              throw new RuntimeException(
                "ClusterReceptionist could not be loaded dynamically. Make sure you have " +
                "'pekko-cluster-typed' in the classpath.",
                e)
          }
          .get
      } else LocalReceptionist

    import pekko.actor.typed.scaladsl.adapter._
    system.internalSystemActorOf(
      provider.behavior,
      provider.name,
      Props.empty.withDispatcherFromConfig(Dispatchers.InternalDispatcherId))
  }
}
