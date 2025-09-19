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

import scala.collection.immutable

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.PostStop
import pekko.actor.typed.PreRestart
import pekko.actor.typed.Signal
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.annotation.InternalApi
import pekko.annotation.InternalStableApi
import pekko.persistence._
import pekko.persistence.JournalProtocol.ReplayMessages
import pekko.persistence.SnapshotProtocol.LoadSnapshot
import pekko.util.{ unused, OptionVal }

/** INTERNAL API */
@InternalApi
private[pekko] object JournalInteractions {

  type EventOrTaggedOrReplicated = Any // `Any` since can be `E` or `Tagged` or a `ReplicatedEvent`

  final case class EventToPersist(
      adaptedEvent: EventOrTaggedOrReplicated,
      manifest: String,
      metadata: Option[ReplicatedEventMetadata])

}

/** INTERNAL API */
@InternalApi
private[pekko] trait JournalInteractions[C, E, S] {

  import JournalInteractions._

  def setup: BehaviorSetup[C, E, S]

  protected def internalPersist(
      ctx: ActorContext[_],
      cmd: Any,
      state: Running.RunningState[S, C],
      event: EventOrTaggedOrReplicated,
      eventAdapterManifest: String,
      metadata: OptionVal[Any]): Running.RunningState[S, C] = {

    val newRunningState = state.nextSequenceNr()

    val repr = PersistentRepr(
      event,
      persistenceId = setup.persistenceId.id,
      sequenceNr = newRunningState.seqNr,
      manifest = eventAdapterManifest,
      writerUuid = setup.writerIdentity.writerUuid,
      sender = ActorRef.noSender)

    // FIXME check cinnamon is okay with this being null
    // https://github.com/akka/akka/issues/29262
    onWriteInitiated(ctx, cmd, repr)

    val write = AtomicWrite(metadata match {
      case OptionVal.Some(meta) => repr.withMetadata(meta)
      case _                    => repr
    }) :: Nil

    setup.journal
      .tell(JournalProtocol.WriteMessages(write, setup.selfClassic, setup.writerIdentity.instanceId), setup.selfClassic)

    newRunningState
  }

  @InternalStableApi
  private[pekko] def onWriteInitiated(
      @unused ctx: ActorContext[_],
      @unused cmd: Any,
      @unused repr: PersistentRepr): Unit = ()

  protected def internalPersistAll(
      ctx: ActorContext[_],
      cmd: Any,
      state: Running.RunningState[S, C],
      events: immutable.Seq[EventToPersist]): Running.RunningState[S, C] = {
    if (events.nonEmpty) {
      var newState = state

      val writes = events.map {
        case EventToPersist(event, eventAdapterManifest, metadata) =>
          newState = newState.nextSequenceNr()
          val repr = PersistentRepr(
            event,
            persistenceId = setup.persistenceId.id,
            sequenceNr = newState.seqNr,
            manifest = eventAdapterManifest,
            writerUuid = setup.writerIdentity.writerUuid,
            sender = ActorRef.noSender)
          metadata match {
            case Some(metadata) => repr.withMetadata(metadata)
            case None           => repr
          }
      }

      onWritesInitiated(ctx, cmd, writes)
      val write = AtomicWrite(writes)

      setup.journal.tell(
        JournalProtocol.WriteMessages(write :: Nil, setup.selfClassic, setup.writerIdentity.instanceId),
        setup.selfClassic)

      newState
    } else state
  }

  @InternalStableApi
  private[pekko] def onWritesInitiated(
      @unused ctx: ActorContext[_],
      @unused cmd: Any,
      @unused repr: immutable.Seq[PersistentRepr]): Unit = ()

  protected def replayEvents(fromSeqNr: Long, toSeqNr: Long): Unit = {
    setup.internalLogger.debug2("Replaying events: from: {}, to: {}", fromSeqNr, toSeqNr)
    setup.journal.tell(
      ReplayMessages(fromSeqNr, toSeqNr, setup.recovery.replayMax, setup.persistenceId.id, setup.selfClassic),
      setup.selfClassic)
  }

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

  /**
   * On [[pekko.persistence.SaveSnapshotSuccess]], if `SnapshotCountRetentionCriteria.deleteEventsOnSnapshot`
   * is enabled, old messages are deleted based on `SnapshotCountRetentionCriteria.snapshotEveryNEvents`
   * before old snapshots are deleted.
   */
  protected def internalDeleteEvents(lastSequenceNr: Long, toSequenceNr: Long): Unit = {
    if (setup.isSnapshotOptional) {
      setup.internalLogger.warn(
        "Delete events shouldn't be used together with snapshot-is-optional=false. " +
        "That can result in wrong recovered state if snapshot load fails.")
    }
    if (toSequenceNr > 0) {
      val self = setup.selfClassic

      if (toSequenceNr == Long.MaxValue || toSequenceNr <= lastSequenceNr) {
        setup.internalLogger.debug("Deleting events up to sequenceNr [{}]", toSequenceNr)
        setup.journal.tell(JournalProtocol.DeleteMessagesTo(setup.persistenceId.id, toSequenceNr, self), self)
      } else
        self ! DeleteMessagesFailure(
          new RuntimeException(
            s"toSequenceNr [$toSequenceNr] must be less than or equal to lastSequenceNr [$lastSequenceNr]"),
          toSequenceNr)
    }
  }
}

/** INTERNAL API */
@InternalApi
private[pekko] trait SnapshotInteractions[C, E, S] {

  def setup: BehaviorSetup[C, E, S]

  /**
   * Instructs the snapshot store to load the specified snapshot and send it via an [[SnapshotOffer]]
   * to the running [[PersistentActor]].
   */
  protected def loadSnapshot(criteria: SnapshotSelectionCriteria, toSequenceNr: Long): Unit = {
    setup.snapshotStore.tell(LoadSnapshot(setup.persistenceId.id, criteria, toSequenceNr), setup.selfClassic)
  }

  protected def internalSaveSnapshot(state: Running.RunningState[S, C]): Unit = {
    setup.internalLogger.debug("Saving snapshot sequenceNr [{}]", state.seqNr)
    if (state.state == null)
      throw new IllegalStateException("A snapshot must not be a null state.")
    else {
      val meta = setup.replication match {
        case Some(_) =>
          val m = ReplicatedSnapshotMetadata(state.version, state.seenPerReplica)
          Some(m)
        case None => None
      }
      setup.snapshotStore.tell(
        SnapshotProtocol.SaveSnapshot(
          new SnapshotMetadata(setup.persistenceId.id, state.seqNr, meta),
          setup.snapshotAdapter.toJournal(state.state)),
        setup.selfClassic)
    }
  }

  /** Deletes the snapshots up to and including the `sequenceNr`. */
  protected def internalDeleteSnapshots(fromSequenceNr: Long, toSequenceNr: Long): Unit = {
    if (toSequenceNr > 0) {
      val snapshotCriteria = SnapshotSelectionCriteria(minSequenceNr = fromSequenceNr, maxSequenceNr = toSequenceNr)
      setup.internalLogger.debug2("Deleting snapshots from sequenceNr [{}] to [{}]", fromSequenceNr, toSequenceNr)
      setup.snapshotStore
        .tell(SnapshotProtocol.DeleteSnapshots(setup.persistenceId.id, snapshotCriteria), setup.selfClassic)
    }
  }
}
