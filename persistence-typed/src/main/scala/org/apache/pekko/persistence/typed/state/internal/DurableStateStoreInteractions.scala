/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.internal

import scala.util.Failure
import scala.util.Success

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.Behavior
import pekko.actor.typed.PostStop
import pekko.actor.typed.PreRestart
import pekko.actor.typed.Signal
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.annotation.InternalApi
import pekko.annotation.InternalStableApi
import pekko.persistence._
import pekko.persistence.state.scaladsl.GetObjectResult
import pekko.util.unused

/** INTERNAL API */
@InternalApi
private[pekko] trait DurableStateStoreInteractions[C, S] {

  def setup: BehaviorSetup[C, S]

  // FIXME use CircuitBreaker here, that can also replace the RecoveryTimeout

  protected def internalGet(ctx: ActorContext[InternalProtocol]): Unit = {
    val persistenceId = setup.persistenceId.id
    ctx.pipeToSelf[GetObjectResult[Any]](setup.durableStateStore.getObject(persistenceId)) {
      case Success(state) => InternalProtocol.GetSuccess(state)
      case Failure(cause) => InternalProtocol.GetFailure(cause)
    }
  }

  protected def internalUpsert(
      ctx: ActorContext[InternalProtocol],
      cmd: Any,
      state: Running.RunningState[S],
      value: Any): Running.RunningState[S] = {

    val newRunningState = state.nextRevision()
    val persistenceId = setup.persistenceId.id

    onWriteInitiated(ctx, cmd)

    ctx.pipeToSelf[Done](
      setup.durableStateStore.upsertObject(persistenceId, newRunningState.revision, value, setup.tag)) {
      case Success(_)     => InternalProtocol.UpsertSuccess
      case Failure(cause) => InternalProtocol.UpsertFailure(cause)
    }

    newRunningState
  }

  protected def internalDelete(
      ctx: ActorContext[InternalProtocol],
      cmd: Any,
      state: Running.RunningState[S]): Running.RunningState[S] = {

    val newRunningState = state.nextRevision().copy(state = setup.emptyState)
    val persistenceId = setup.persistenceId.id

    onDeleteInitiated(ctx, cmd)

    ctx.pipeToSelf[Done](setup.durableStateStore.deleteObject(persistenceId, newRunningState.revision)) {
      case Success(_)     => InternalProtocol.DeleteSuccess
      case Failure(cause) => InternalProtocol.DeleteFailure(cause)
    }

    newRunningState
  }

  // FIXME These hook methods are for Telemetry. What more parameters are needed? persistenceId?
  @InternalStableApi
  private[pekko] def onWriteInitiated(@unused ctx: ActorContext[?], @unused cmd: Any): Unit = ()

  private[pekko] def onDeleteInitiated(@unused ctx: ActorContext[?], @unused cmd: Any): Unit = ()

  protected def requestRecoveryPermit(): Unit = {
    setup.persistence.recoveryPermitter.tell(RecoveryPermitter.RequestRecoveryPermit, setup.selfClassic)
  }

  /** Intended to be used in .onSignal(returnPermitOnStop) by behaviors */
  protected def returnPermitOnStop
      : PartialFunction[(ActorContext[InternalProtocol], Signal), Behavior[InternalProtocol]] = {
    case (_, PostStop) =>
      tryReturnRecoveryPermit("PostStop")
      Behaviors.stopped
    case (_, PreRestart) =>
      tryReturnRecoveryPermit("PreRestart")
      Behaviors.stopped
  }

  /** Mutates setup, by setting the `holdingRecoveryPermit` to false */
  protected def tryReturnRecoveryPermit(reason: String): Unit = {
    if (setup.holdingRecoveryPermit) {
      setup.internalLogger.debug("Returning recovery permit, reason: {}", reason)
      setup.persistence.recoveryPermitter.tell(RecoveryPermitter.ReturnRecoveryPermit, setup.selfClassic)
      setup.holdingRecoveryPermit = false
    } // else, no need to return the permit
  }

}
