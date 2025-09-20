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

import scala.annotation.nowarn
import scala.annotation.tailrec
import scala.collection.immutable

import org.apache.pekko
import pekko.actor.UnhandledMessage
import pekko.actor.typed.Behavior
import pekko.actor.typed.Signal
import pekko.actor.typed.internal.PoisonPill
import pekko.actor.typed.scaladsl.AbstractBehavior
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.annotation.InternalApi
import pekko.annotation.InternalStableApi
import pekko.persistence.typed.state.internal.DurableStateBehaviorImpl.GetState
import pekko.persistence.typed.state.scaladsl.Effect

/**
 * INTERNAL API
 *
 * Conceptually third (of three) -- also known as 'final' or 'ultimate' -- form of `DurableStateBehavior`.
 *
 * In this phase recovery has completed successfully and we continue handling incoming commands,
 * as well as persisting new state as dictated by the user handlers.
 *
 * This behavior operates in two phases (also behaviors):
 * - HandlingCommands - where the command handler is invoked for incoming commands
 * - PersistingState - where incoming commands are stashed until persistence completes
 *
 * This is implemented as such to avoid creating many Running instances,
 * which perform the Persistence extension lookup on creation and similar things (config lookup)
 *
 * See previous [[Recovering]].
 */
@InternalApi
private[pekko] object Running {

  trait WithRevisionAccessible {
    def currentRevision: Long
  }

  // This is part of the fix for https://github.com/apache/pekko/issues/1327 and it's necessary to break
  // recursion between the `onCommand` and `onMessage` functions in Running[C, E, S]#HandlingCommands.
  // See comment in org.apache.pekko.persistence.typed.internal.Running#UnstashRecurrenceState for more
  // information
  final class UnstashRecurrenceState[State, Command] {
    var inOnCommandCall: Boolean = false
    var recOnCommandParams: Option[(RunningState[State, Command], Command)] = None
  }

  final case class RunningState[State, Command](
      revision: Long,
      state: State,
      receivedPoisonPill: Boolean,
      unstashRecurrenceState: UnstashRecurrenceState[State, Command] = new UnstashRecurrenceState[State, Command]) {

    def nextRevision(): RunningState[State, Command] =
      copy(revision = revision + 1)

    def applyState(@nowarn("msg=never used") setup: BehaviorSetup[Command, State], updated: State)
        : RunningState[State, Command] = {
      copy(state = updated)
    }
  }
}

// ===============================================

/** INTERNAL API */
@InternalApi private[pekko] final class Running[C, S](override val setup: BehaviorSetup[C, S])
    extends DurableStateStoreInteractions[C, S]
    with StashManagement[C, S] {
  import InternalProtocol._
  import Running.RunningState
  import Running.WithRevisionAccessible

  // Needed for WithSeqNrAccessible, when unstashing
  private var _currentRevision = 0L

  final class HandlingCommands(state: RunningState[S, C])
      extends AbstractBehavior[InternalProtocol](setup.context)
      with WithRevisionAccessible {

    _currentRevision = state.revision

    def onMessage(msg: InternalProtocol): Behavior[InternalProtocol] = msg match {
      case IncomingCommand(c: C @unchecked) if setup.settings.recurseWhenUnstashingReadOnlyCommands =>
        onCommand(state, c)
      case IncomingCommand(c: C @unchecked) =>
        if (state.unstashRecurrenceState.inOnCommandCall) {
          state.unstashRecurrenceState.recOnCommandParams = Some((state, c))
          this // This will be ignored in onCommand
        } else {
          onCommand(state, c)
        }
      case get: GetState[S @unchecked] => onGetState(get)
      case _                           => Behaviors.unhandled
    }

    override def onSignal: PartialFunction[Signal, Behavior[InternalProtocol]] = {
      case PoisonPill =>
        if (isInternalStashEmpty && !isUnstashAllInProgress) Behaviors.stopped
        else new HandlingCommands(state.copy(receivedPoisonPill = true))
      case signal =>
        if (setup.onSignal(state.state, signal, catchAndLog = false)) this
        else Behaviors.unhandled
    }

    def onCommand(state: RunningState[S, C], cmd: C): Behavior[InternalProtocol] = {
      def callApplyEffects(rs: RunningState[S, C], c: C): (Behavior[InternalProtocol], Boolean) = {
        val effect = setup.commandHandler(rs.state, c)

        applyEffects(c, rs, effect.asInstanceOf[EffectImpl[S]]) // TODO can we avoid the cast?
      }

      def recursiveUnstashImpl(
          applyEffectsRetval: (Behavior[InternalProtocol], Boolean)
      ): Behavior[InternalProtocol] = {
        val (next, doUnstash) = applyEffectsRetval

        if (doUnstash) tryUnstashOne(next)
        else next
      }

      def nonRecursiveUnstashImpl(
          applyEffectsRetval: (Behavior[InternalProtocol], Boolean)
      ): Behavior[InternalProtocol] = {
        @tailrec
        def loop(applyEffectsRetval: (Behavior[InternalProtocol], Boolean)): Behavior[InternalProtocol] = {
          val (next, doUnstash) = applyEffectsRetval

          if (doUnstash) {
            state.unstashRecurrenceState.inOnCommandCall = true
            val r = tryUnstashOne(next)
            state.unstashRecurrenceState.inOnCommandCall = false

            val recOnCommandParams = state.unstashRecurrenceState.recOnCommandParams
            state.unstashRecurrenceState.recOnCommandParams = None

            recOnCommandParams match {
              case None          => r
              case Some((rs, c)) => loop(callApplyEffects(rs, c))
            }
          } else {
            next
          }
        }

        loop(applyEffectsRetval)
      }

      val r = callApplyEffects(state, cmd)

      if (setup.settings.recurseWhenUnstashingReadOnlyCommands) {
        recursiveUnstashImpl(r)
      } else {
        nonRecursiveUnstashImpl(r)
      }
    }

    // Used by DurableStateBehaviorTestKit to retrieve the state.
    def onGetState(get: GetState[S]): Behavior[InternalProtocol] = {
      get.replyTo ! state.state
      this
    }

    private def handlePersist(
        newState: S,
        cmd: Any,
        sideEffects: immutable.Seq[SideEffect[S]]): (Behavior[InternalProtocol], Boolean) = {
      _currentRevision = state.revision + 1

      val stateAfterApply = state.applyState(setup, newState)
      val stateToPersist = adaptState(newState)

      val newState2 =
        internalUpsert(setup.context, cmd, stateAfterApply, stateToPersist)

      (persistingState(newState2, state, sideEffects), false)
    }

    @tailrec def applyEffects(
        msg: Any,
        state: RunningState[S, C],
        effect: Effect[S],
        sideEffects: immutable.Seq[SideEffect[S]] = Nil): (Behavior[InternalProtocol], Boolean) = {
      if (setup.internalLogger.isDebugEnabled && !effect.isInstanceOf[CompositeEffect[_]])
        setup.internalLogger.debugN(
          s"Handled command [{}], resulting effect: [{}], side effects: [{}]",
          msg.getClass.getName,
          effect,
          sideEffects.size)

      effect match {
        case CompositeEffect(eff, currentSideEffects: Seq[SideEffect[S @unchecked]]) =>
          // unwrap and accumulate effects
          applyEffects(msg, state, eff, currentSideEffects ++ sideEffects)

        case Persist(newState) =>
          handlePersist(newState, msg, sideEffects)

        case _: PersistNothing.type =>
          (applySideEffects(sideEffects, state), true)

        case _: Delete[_] =>
          val nextState = internalDelete(setup.context, msg, state)
          (applySideEffects(sideEffects, nextState), true)

        case _: Unhandled.type =>
          import pekko.actor.typed.scaladsl.adapter._
          setup.context.system.toClassic.eventStream
            .publish(UnhandledMessage(msg, setup.context.system.toClassic.deadLetters, setup.context.self.toClassic))
          (applySideEffects(sideEffects, state), true)

        case _: Stash.type =>
          stashUser(IncomingCommand(msg))
          (applySideEffects(sideEffects, state), true)

        case unexpected => throw new IllegalStateException(s"Unexpected effect: $unexpected")
      }
    }

    def adaptState(newState: S): Any = {
      setup.snapshotAdapter.toJournal(newState)
    }

    setup.setMdcPhase(PersistenceMdc.RunningCmds)

    override def currentRevision: Long =
      _currentRevision
  }

  // ===============================================

  def persistingState(
      state: RunningState[S, C],
      visibleState: RunningState[S, C], // previous state until write success
      sideEffects: immutable.Seq[SideEffect[S]]): Behavior[InternalProtocol] = {
    setup.setMdcPhase(PersistenceMdc.PersistingState)
    new PersistingState(state, visibleState, sideEffects)
  }

  /** INTERNAL API */
  @InternalApi private[pekko] class PersistingState(
      var state: RunningState[S, C],
      var visibleState: RunningState[S, C], // previous state until write success
      var sideEffects: immutable.Seq[SideEffect[S]],
      persistStartTime: Long = System.nanoTime())
      extends AbstractBehavior[InternalProtocol](setup.context)
      with WithRevisionAccessible {

    override def onMessage(msg: InternalProtocol): Behavior[InternalProtocol] = {
      msg match {
        case UpsertSuccess                     => onUpsertSuccess()
        case UpsertFailure(exc)                => onUpsertFailed(exc)
        case in: IncomingCommand[C @unchecked] => onCommand(in)
        case get: GetState[S @unchecked]       => stashInternal(get)
        case RecoveryTimeout                   => Behaviors.unhandled
        case RecoveryPermitGranted             => Behaviors.unhandled
        case _: GetSuccess[_]                  => Behaviors.unhandled
        case _: GetFailure                     => Behaviors.unhandled
        case DeleteSuccess                     => Behaviors.unhandled
        case DeleteFailure(_)                  => Behaviors.unhandled
      }
    }

    def onCommand(cmd: IncomingCommand[C]): Behavior[InternalProtocol] = {
      if (state.receivedPoisonPill) {
        if (setup.settings.logOnStashing)
          setup.internalLogger.debug("Discarding message [{}], because actor is to be stopped.", cmd)
        Behaviors.unhandled
      } else {
        stashInternal(cmd)
      }
    }

    final def onUpsertSuccess(): Behavior[InternalProtocol] = {
      if (setup.internalLogger.isDebugEnabled) {
        setup.internalLogger
          .debug("Received UpsertSuccess response after: {} nanos", System.nanoTime() - persistStartTime)
      }

      onWriteSuccess(setup.context)

      visibleState = state
      val newState = applySideEffects(sideEffects, state)
      tryUnstashOne(newState)
    }

    final def onUpsertFailed(cause: Throwable): Behavior[InternalProtocol] = {
      onWriteFailed(setup.context, cause)
      throw new DurableStateStoreException(setup.persistenceId, currentRevision, cause)
    }

    override def onSignal: PartialFunction[Signal, Behavior[InternalProtocol]] = {
      case PoisonPill =>
        // wait for store responses before stopping
        state = state.copy(receivedPoisonPill = true)
        this
      case signal =>
        if (setup.onSignal(visibleState.state, signal, catchAndLog = false)) this
        else Behaviors.unhandled
    }

    override def currentRevision: Long = {
      _currentRevision
    }
  }

  // ===============================================

  def applySideEffects(effects: immutable.Seq[SideEffect[S]], state: RunningState[S, C]): Behavior[InternalProtocol] = {
    var behavior: Behavior[InternalProtocol] = new HandlingCommands(state)
    val it = effects.iterator

    // if at least one effect results in a `stop`, we need to stop
    // manual loop implementation to avoid allocations and multiple scans
    while (it.hasNext) {
      val effect = it.next()
      behavior = applySideEffect(effect, state, behavior)
    }

    if (state.receivedPoisonPill && isInternalStashEmpty && !isUnstashAllInProgress)
      Behaviors.stopped
    else
      behavior
  }

  def applySideEffect(
      effect: SideEffect[S],
      state: RunningState[S, C],
      behavior: Behavior[InternalProtocol]): Behavior[InternalProtocol] = {
    effect match {
      case _: Stop.type @unchecked =>
        Behaviors.stopped

      case _: UnstashAll.type @unchecked =>
        unstashAll()
        behavior

      case callback: Callback[_] =>
        callback.sideEffect(state.state)
        behavior
    }
  }

  @InternalStableApi
  private[pekko] def onWriteFailed(@nowarn("msg=never used") ctx: ActorContext[_],
      @nowarn("msg=never used") reason: Throwable): Unit = ()
  @InternalStableApi
  private[pekko] def onWriteSuccess(@nowarn("msg=never used") ctx: ActorContext[_]): Unit = ()

}
