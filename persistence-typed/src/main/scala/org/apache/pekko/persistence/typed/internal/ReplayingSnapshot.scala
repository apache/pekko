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

package org.apache.pekko.persistence.typed.internal

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.internal.PoisonPill
import pekko.actor.typed.scaladsl.{ ActorContext, Behaviors }
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.annotation.{ InternalApi, InternalStableApi }
import pekko.persistence._
import pekko.persistence.SnapshotProtocol.LoadSnapshotFailed
import pekko.persistence.SnapshotProtocol.LoadSnapshotResult
import pekko.persistence.typed.{ RecoveryFailed, ReplicaId }
import pekko.persistence.typed.internal.EventSourcedBehaviorImpl.{ GetSeenSequenceNr, GetState }

/**
 * INTERNAL API
 *
 * Second (of four) behavior of an EventSourcedBehavior.
 *
 * In this behavior the recovery process is initiated.
 * We try to obtain a snapshot from the configured snapshot store,
 * and if it exists, we use it instead of the initial `emptyState`.
 *
 * Once snapshot recovery is done (or no snapshot was selected),
 * recovery of events continues in [[ReplayingEvents]].
 *
 * See next behavior [[ReplayingEvents]].
 * See previous behavior [[RequestingRecoveryPermit]].
 */
@InternalApi
private[pekko] object ReplayingSnapshot {

  def apply[C, E, S](setup: BehaviorSetup[C, E, S], receivedPoisonPill: Boolean): Behavior[InternalProtocol] =
    new ReplayingSnapshot(setup.setMdcPhase(PersistenceMdc.ReplayingSnapshot)).createBehavior(receivedPoisonPill)

}

@InternalApi
private[pekko] class ReplayingSnapshot[C, E, S](override val setup: BehaviorSetup[C, E, S])
    extends JournalInteractions[C, E, S]
    with SnapshotInteractions[C, E, S]
    with StashManagement[C, E, S] {

  import InternalProtocol._

  onRecoveryStart(setup.context)

  def createBehavior(receivedPoisonPillInPreviousPhase: Boolean): Behavior[InternalProtocol] = {
    // protect against snapshot stalling forever because of journal overloaded and such
    setup.startRecoveryTimer(snapshot = true)

    loadSnapshot(setup.recovery.fromSnapshot, setup.recovery.toSequenceNr)

    def stay(receivedPoisonPill: Boolean): Behavior[InternalProtocol] = {
      Behaviors
        .receiveMessage[InternalProtocol] {
          case SnapshotterResponse(r)                     => onSnapshotterResponse(r, receivedPoisonPill)
          case JournalResponse(r)                         => onJournalResponse(r)
          case RecoveryTickEvent(snapshot)                => onRecoveryTick(snapshot)
          case evt: ReplicatedEventEnvelope[E @unchecked] => onReplicatedEvent(evt)
          case pe: PublishedEventImpl                     => onPublishedEvent(pe)
          case cmd: IncomingCommand[C @unchecked]         =>
            if (receivedPoisonPill) {
              if (setup.settings.logOnStashing)
                setup.internalLogger.debug("Discarding message [{}], because actor is to be stopped.", cmd)
              Behaviors.unhandled
            } else
              onCommand(cmd)
          case get: GetState[S @unchecked] => stashInternal(get)
          case get: GetSeenSequenceNr      => stashInternal(get)
          case RecoveryPermitGranted       => Behaviors.unhandled // should not happen, we already have the permit
        }
        .receiveSignal(returnPermitOnStop.orElse {
          case (_, PoisonPill) =>
            stay(receivedPoisonPill = true)
          case (_, signal) =>
            if (setup.onSignal(setup.emptyState, signal, catchAndLog = true)) Behaviors.same
            else Behaviors.unhandled
        })
    }
    stay(receivedPoisonPillInPreviousPhase)
  }

  /**
   * Called whenever snapshot recovery fails.
   *
   * This method throws `JournalFailureException` which will be caught by the internal
   * supervision strategy to stop or restart the actor with backoff.
   *
   * @param cause failure cause.
   */
  private def onRecoveryFailure(cause: Throwable): Behavior[InternalProtocol] = {
    onRecoveryFailed(setup.context, cause)
    setup.onSignal(setup.emptyState, RecoveryFailed(cause), catchAndLog = true)
    setup.cancelRecoveryTimer()

    tryReturnRecoveryPermit("on snapshot recovery failure: " + cause.getMessage)

    if (setup.internalLogger.isDebugEnabled)
      setup.internalLogger.debug("Recovery failure for persistenceId [{}]", setup.persistenceId)

    val msg = s"Exception during recovery from snapshot. " +
      s"PersistenceId [${setup.persistenceId.id}]. ${cause.getMessage}"
    throw new JournalFailureException(msg, cause)
  }

  @InternalStableApi
  def onRecoveryStart(@nowarn("msg=never used") context: ActorContext[_]): Unit = ()
  @InternalStableApi
  def onRecoveryFailed(@nowarn("msg=never used") context: ActorContext[_], @nowarn("msg=never used") reason: Throwable)
      : Unit = ()

  private def onRecoveryTick(snapshot: Boolean): Behavior[InternalProtocol] =
    if (snapshot) {
      // we know we're in snapshotting mode; snapshot recovery timeout arrived
      val ex = new RecoveryTimedOut(
        s"Recovery timed out, didn't get snapshot within ${setup.recoveryEventTimeout}")
      onRecoveryFailure(ex)
    } else Behaviors.same // ignore, since we received the snapshot already

  def onCommand(cmd: IncomingCommand[C]): Behavior[InternalProtocol] = {
    // during recovery, stash all incoming commands
    stashInternal(cmd)
  }

  def onReplicatedEvent(evt: InternalProtocol.ReplicatedEventEnvelope[E]): Behavior[InternalProtocol] = {
    stashInternal(evt)
  }

  def onPublishedEvent(event: PublishedEventImpl): Behavior[InternalProtocol] = {
    stashInternal(event)
  }

  def onJournalResponse(response: JournalProtocol.Response): Behavior[InternalProtocol] = {
    setup.internalLogger.debug(
      "Unexpected response from journal: [{}], may be due to an actor restart, ignoring...",
      response.getClass.getName)
    Behaviors.unhandled
  }

  def onSnapshotterResponse(
      response: SnapshotProtocol.Response,
      receivedPoisonPill: Boolean): Behavior[InternalProtocol] = {

    def loadSnapshotResult(snapshot: Option[SelectedSnapshot], toSnr: Long): Behavior[InternalProtocol] = {
      var state: S = setup.emptyState

      val (seqNr: Long, seenPerReplica, version) = snapshot match {
        case Some(SelectedSnapshot(metadata, snapshot)) =>
          state = setup.snapshotAdapter.fromJournal(snapshot)
          setup.internalLogger.debug("Loaded snapshot with metadata [{}]", metadata)
          metadata.metadata match {
            case Some(rm: ReplicatedSnapshotMetadata) => (metadata.sequenceNr, rm.seenPerReplica, rm.version)
            case _                                    => (metadata.sequenceNr, Map.empty[ReplicaId, Long].withDefaultValue(0L), VersionVector.empty)
          }
        case None => (0L, Map.empty[ReplicaId, Long].withDefaultValue(0L), VersionVector.empty)
      }

      setup.internalLogger.debugN("Snapshot recovered from {} {} {}", seqNr, seenPerReplica, version)

      setup.cancelRecoveryTimer()

      ReplayingEvents[C, E, S](
        setup,
        ReplayingEvents.ReplayingState(
          seqNr,
          state,
          eventSeenInInterval = false,
          toSnr,
          receivedPoisonPill,
          System.nanoTime(),
          version,
          seenPerReplica,
          eventsReplayed = 0))
    }

    response match {
      case LoadSnapshotResult(snapshot, toSnr) =>
        loadSnapshotResult(snapshot, toSnr)

      case LoadSnapshotFailed(cause) =>
        if (setup.isSnapshotOptional) {
          setup.internalLogger.info(
            "Snapshot load error for persistenceId [{}]. Replaying all events since snapshot-is-optional=true",
            setup.persistenceId)

          loadSnapshotResult(snapshot = None, setup.recovery.toSequenceNr)
        } else {
          onRecoveryFailure(cause)
        }

      case _ =>
        Behaviors.unhandled
    }
  }

}
