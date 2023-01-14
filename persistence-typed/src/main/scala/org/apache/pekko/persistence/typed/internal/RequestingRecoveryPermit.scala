/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.internal

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.internal.PoisonPill
import pekko.actor.typed.scaladsl.{ ActorContext, Behaviors }
import pekko.annotation.{ InternalApi, InternalStableApi }
import pekko.util.unused

/**
 * INTERNAL API
 *
 * First (of four) behavior of an PersistentBehaviour.
 *
 * Requests a permit to start replaying this actor; this is tone to avoid
 * hammering the journal with too many concurrently replaying actors.
 *
 * See next behavior [[ReplayingSnapshot]].
 */
@InternalApi
private[pekko] object RequestingRecoveryPermit {

  def apply[C, E, S](setup: BehaviorSetup[C, E, S]): Behavior[InternalProtocol] =
    new RequestingRecoveryPermit(setup.setMdcPhase(PersistenceMdc.AwaitingPermit)).createBehavior()

}

@InternalApi
private[pekko] class RequestingRecoveryPermit[C, E, S](override val setup: BehaviorSetup[C, E, S])
    extends StashManagement[C, E, S]
    with JournalInteractions[C, E, S]
    with SnapshotInteractions[C, E, S] {

  onRequestingRecoveryPermit(setup.context)

  def createBehavior(): Behavior[InternalProtocol] = {
    // request a permit, as only once we obtain one we can start replaying
    requestRecoveryPermit()

    def stay(receivedPoisonPill: Boolean): Behavior[InternalProtocol] = {
      Behaviors
        .receiveMessage[InternalProtocol] {
          case InternalProtocol.RecoveryPermitGranted =>
            becomeReplaying(receivedPoisonPill)

          case other =>
            if (receivedPoisonPill) {
              if (setup.settings.logOnStashing)
                setup.internalLogger.debug("Discarding message [{}], because actor is to be stopped.", other)
              Behaviors.unhandled
            } else {
              stashInternal(other)
            }

        }
        .receiveSignal {
          case (_, PoisonPill) =>
            stay(receivedPoisonPill = true)
          case (_, signal) =>
            if (setup.onSignal(setup.emptyState, signal, catchAndLog = true)) Behaviors.same
            else Behaviors.unhandled
        }
    }
    stay(receivedPoisonPill = false)
  }

  @InternalStableApi
  def onRequestingRecoveryPermit(@unused context: ActorContext[_]): Unit = ()

  private def becomeReplaying(receivedPoisonPill: Boolean): Behavior[InternalProtocol] = {
    setup.internalLogger.debug(s"Initializing snapshot recovery: {}", setup.recovery)

    setup.holdingRecoveryPermit = true
    ReplayingSnapshot(setup, receivedPoisonPill)
  }

}
