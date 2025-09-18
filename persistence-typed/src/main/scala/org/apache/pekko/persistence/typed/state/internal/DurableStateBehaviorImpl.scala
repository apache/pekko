/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.internal

import org.apache.pekko
import pekko.actor.typed
import pekko.actor.typed.ActorRef
import pekko.actor.typed.BackoffSupervisorStrategy
import pekko.actor.typed.Behavior
import pekko.actor.typed.BehaviorInterceptor
import pekko.actor.typed.PostStop
import pekko.actor.typed.Signal
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.internal.ActorContextImpl
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.annotation._
import pekko.persistence.RecoveryPermitter
import pekko.persistence.state.exception.DurableStateException
import pekko.persistence.state.scaladsl.GetObjectResult
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.SnapshotAdapter
import pekko.persistence.typed.state.scaladsl._
import pekko.util.unused

import org.slf4j.LoggerFactory

@InternalApi
private[pekko] object DurableStateBehaviorImpl {

  /**
   * Used by DurableStateBehaviorTestKit to retrieve the `persistenceId`.
   */
  final case class GetPersistenceId(replyTo: ActorRef[PersistenceId]) extends Signal

  /**
   * Used by DurableStateBehaviorTestKit to retrieve the state.
   * Can't be a Signal because those are not stashed.
   */
  final case class GetState[State](replyTo: ActorRef[State]) extends InternalProtocol

}

@InternalApi
private[pekko] final case class DurableStateBehaviorImpl[Command, State](
    persistenceId: PersistenceId,
    emptyState: State,
    commandHandler: DurableStateBehavior.CommandHandler[Command, State],
    loggerClass: Class[_],
    durableStateStorePluginId: Option[String] = None,
    tag: String = "",
    snapshotAdapter: SnapshotAdapter[State] = NoOpSnapshotAdapter.instance[State],
    supervisionStrategy: SupervisorStrategy = SupervisorStrategy.stop,
    override val signalHandler: PartialFunction[(State, Signal), Unit] = PartialFunction.empty)
    extends DurableStateBehavior[Command, State] {

  if (persistenceId eq null)
    throw new IllegalArgumentException("persistenceId must not be null")

  // Don't use it directly, but instead call internalLogger() (see below)
  private val loggerForInternal = LoggerFactory.getLogger(this.getClass)

  override def apply(context: typed.TypedActorContext[Command]): Behavior[Command] = {
    val ctx = context.asScala
    val hasCustomLoggerName = ctx match {
      case internalCtx: ActorContextImpl[_] => internalCtx.hasCustomLoggerName
      case _                                => false
    }
    if (!hasCustomLoggerName) ctx.setLoggerName(loggerClass)
    val settings = DurableStateSettings(ctx.system, durableStateStorePluginId.getOrElse(""))

    // stashState outside supervise because StashState should survive restarts due to persist failures
    val stashState = new StashState(ctx.asInstanceOf[ActorContext[InternalProtocol]], settings)

    // This method ensures that the MDC is set before we use the internal logger
    def internalLogger() = {
      if (settings.useContextLoggerForInternalLogging) ctx.log
      else {
        // MDC is cleared (if used) from aroundReceive in ActorAdapter after processing each message,
        // but important to call `context.log` to mark MDC as used
        ctx.log
        loggerForInternal
      }
    }

    val actualSignalHandler: PartialFunction[(State, Signal), Unit] = signalHandler.orElse {
      // default signal handler is always the fallback
      case (_, DurableStateBehaviorImpl.GetPersistenceId(replyTo)) => replyTo ! persistenceId
    }

    // do this once, even if the actor is restarted
    initialize(context.asScala)

    Behaviors
      .supervise {
        Behaviors.setup[Command] { _ =>
          val durableStateSetup = new BehaviorSetup(
            ctx.asInstanceOf[ActorContext[InternalProtocol]],
            persistenceId,
            emptyState,
            commandHandler,
            actualSignalHandler,
            tag,
            snapshotAdapter,
            holdingRecoveryPermit = false,
            settings = settings,
            stashState = stashState,
            internalLoggerFactory = () => internalLogger())

          // needs to accept Any since we also can get messages from outside
          // not part of the user facing Command protocol
          def interceptor: BehaviorInterceptor[Any, InternalProtocol] = new BehaviorInterceptor[Any, InternalProtocol] {

            import BehaviorInterceptor._
            override def aroundReceive(
                ctx: typed.TypedActorContext[Any],
                msg: Any,
                target: ReceiveTarget[InternalProtocol]): Behavior[InternalProtocol] = {
              val innerMsg = msg match {
                case RecoveryPermitter.RecoveryPermitGranted => InternalProtocol.RecoveryPermitGranted
                case internal: InternalProtocol              => internal // such as RecoveryTimeout
                case cmd                                     => InternalProtocol.IncomingCommand(cmd.asInstanceOf[Command])
              }
              target(ctx, innerMsg)
            }

            override def aroundSignal(
                ctx: typed.TypedActorContext[Any],
                signal: Signal,
                target: SignalTarget[InternalProtocol]): Behavior[InternalProtocol] = {
              if (signal == PostStop) {
                durableStateSetup.cancelRecoveryTimer()
                // clear stash to be GC friendly
                stashState.clearStashBuffers()
              }
              target(ctx, signal)
            }

            override def toString: String = "DurableStateBehaviorInterceptor"
          }

          Behaviors.intercept(() => interceptor)(RequestingRecoveryPermit(durableStateSetup)).narrow
        }

      }
      .onFailure[DurableStateException](supervisionStrategy)
  }

  @InternalStableApi
  private[pekko] def initialize(@unused context: ActorContext[_]): Unit = ()

  override def receiveSignal(handler: PartialFunction[(State, Signal), Unit]): DurableStateBehavior[Command, State] =
    copy(signalHandler = handler)

  override def withDurableStateStorePluginId(id: String): DurableStateBehavior[Command, State] = {
    require(id != null, "DurableStateBehavior plugin id must not be null; use empty string for 'default' state store")
    copy(durableStateStorePluginId = if (id != "") Some(id) else None)
  }

  override def withTag(tag: String): DurableStateBehavior[Command, State] =
    copy(tag = tag)

  override def snapshotAdapter(adapter: SnapshotAdapter[State]): DurableStateBehavior[Command, State] =
    copy(snapshotAdapter = adapter)

  override def onPersistFailure(backoffStrategy: BackoffSupervisorStrategy): DurableStateBehavior[Command, State] =
    copy(supervisionStrategy = backoffStrategy)

}

/** Protocol used internally by the DurableStateBehavior. */
@InternalApi private[pekko] sealed trait InternalProtocol
@InternalApi private[pekko] object InternalProtocol {
  case object RecoveryPermitGranted extends InternalProtocol
  final case class GetSuccess[S](result: GetObjectResult[S]) extends InternalProtocol
  final case class GetFailure(cause: Throwable) extends InternalProtocol
  case object UpsertSuccess extends InternalProtocol
  final case class UpsertFailure(cause: Throwable) extends InternalProtocol
  case object DeleteSuccess extends InternalProtocol
  final case class DeleteFailure(cause: Throwable) extends InternalProtocol
  case object RecoveryTimeout extends InternalProtocol
  final case class IncomingCommand[C](c: C) extends InternalProtocol

}
