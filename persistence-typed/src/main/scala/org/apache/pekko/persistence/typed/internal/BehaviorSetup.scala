/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.internal

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor.Cancellable
import pekko.actor.typed.Signal
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.{ ActorRef => ClassicActorRef }
import pekko.annotation.InternalApi
import pekko.persistence._
import pekko.persistence.typed.EventAdapter
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.SnapshotAdapter
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.persistence.typed.scaladsl.RetentionCriteria
import pekko.util.OptionVal
import org.slf4j.Logger
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object BehaviorSetup {
  sealed trait SnapshotAfterPersist
  case object NoSnapshot extends SnapshotAfterPersist
  case object SnapshotWithRetention extends SnapshotAfterPersist
  case object SnapshotWithoutRetention extends SnapshotAfterPersist
}

/**
 * INTERNAL API: Carry state for the Persistent behavior implementation behaviors.
 */
@InternalApi
private[pekko] final class BehaviorSetup[C, E, S](
    val context: ActorContext[InternalProtocol],
    val persistenceId: PersistenceId,
    val emptyState: S,
    val commandHandler: EventSourcedBehavior.CommandHandler[C, E, S],
    val eventHandler: EventSourcedBehavior.EventHandler[S, E],
    val writerIdentity: EventSourcedBehaviorImpl.WriterIdentity,
    private val signalHandler: PartialFunction[(S, Signal), Unit],
    val tagger: E => Set[String],
    val eventAdapter: EventAdapter[E, Any],
    val snapshotAdapter: SnapshotAdapter[S],
    val snapshotWhen: (S, E, Long) => Boolean,
    val recovery: Recovery,
    val retention: RetentionCriteria,
    var holdingRecoveryPermit: Boolean,
    val settings: EventSourcedSettings,
    val stashState: StashState,
    val replication: Option[ReplicationSetup],
    val publishEvents: Boolean,
    private val internalLoggerFactory: () => Logger) {

  import BehaviorSetup._
  import InternalProtocol.RecoveryTickEvent
  import pekko.actor.typed.scaladsl.adapter._

  val persistence: Persistence = Persistence(context.system.toClassic)

  val journal: ClassicActorRef = persistence.journalFor(settings.journalPluginId)
  val snapshotStore: ClassicActorRef = persistence.snapshotStoreFor(settings.snapshotPluginId)

  val isSnapshotOptional: Boolean =
    Persistence(context.system.classicSystem).configFor(snapshotStore).getBoolean("snapshot-is-optional")

  if (isSnapshotOptional && (retention match {
      case SnapshotCountRetentionCriteriaImpl(_, _, true) => true
      case _                                              => false
    })) {
    throw new IllegalArgumentException(
      "Retention criteria with delete events can't be used together with snapshot-is-optional=false. " +
      "That can result in wrong recovered state if snapshot load fails.")
  }

  val replicaId: Option[ReplicaId] = replication.map(_.replicaId)

  def selfClassic: ClassicActorRef = context.self.toClassic

  private var mdcPhase = PersistenceMdc.Initializing

  def internalLogger: Logger = {
    PersistenceMdc.setMdc(persistenceId, mdcPhase)
    internalLoggerFactory()
  }

  def setMdcPhase(phaseName: String): BehaviorSetup[C, E, S] = {
    mdcPhase = phaseName
    this
  }

  private var recoveryTimer: OptionVal[Cancellable] = OptionVal.None

  def startRecoveryTimer(snapshot: Boolean): Unit = {
    cancelRecoveryTimer()
    implicit val ec: ExecutionContext = context.executionContext
    val timer =
      if (snapshot)
        context.scheduleOnce(settings.recoveryEventTimeout, context.self, RecoveryTickEvent(snapshot = true))
      else
        context.system.scheduler.scheduleWithFixedDelay(settings.recoveryEventTimeout, settings.recoveryEventTimeout) {
          () =>
            context.self ! RecoveryTickEvent(snapshot = false)
        }
    recoveryTimer = OptionVal.Some(timer)
  }

  def cancelRecoveryTimer(): Unit = {
    recoveryTimer match {
      case OptionVal.Some(t) => t.cancel()
      case _                 =>
    }
    recoveryTimer = OptionVal.None
  }

  /**
   * Applies the `signalHandler` if defined and returns true, otherwise returns false.
   * If an exception is thrown and `catchAndLog=true` it is logged and returns true, otherwise it is thrown.
   *
   * `catchAndLog=true` should be used for "unknown" signals in the phases before Running
   * to avoid restart loops if restart supervision is used.
   */
  def onSignal[T](state: S, signal: Signal, catchAndLog: Boolean): Boolean =
    try {
      var handled = true
      signalHandler.applyOrElse((state, signal), (_: (S, Signal)) => handled = false)
      handled
    } catch {
      case NonFatal(ex) =>
        if (catchAndLog) {
          internalLogger.error(s"Error while processing signal [$signal]: $ex", ex)
          true
        } else {
          if (internalLogger.isDebugEnabled)
            internalLogger.debug(s"Error while processing signal [$signal]: $ex", ex)
          throw ex
        }
    }

  def shouldSnapshot(state: S, event: E, sequenceNr: Long): SnapshotAfterPersist =
    retention match {
      case DisabledRetentionCriteria =>
        if (snapshotWhen(state, event, sequenceNr)) SnapshotWithoutRetention
        else NoSnapshot
      case s: SnapshotCountRetentionCriteriaImpl =>
        if (s.snapshotWhen(sequenceNr)) SnapshotWithRetention
        else if (snapshotWhen(state, event, sequenceNr)) SnapshotWithoutRetention
        else NoSnapshot
      case unexpected => throw new IllegalStateException(s"Unexpected retention criteria: $unexpected")
    }

}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object PersistenceMdc {
  // format: OFF
  val Initializing      = "initializing"
  val AwaitingPermit    = "get-permit"
  val ReplayingSnapshot = "load-snap"
  val ReplayingEvents   = "replay-evt"
  val RunningCmds       = "running-cmd"
  val PersistingEvents  = "persist-evt"
  val StoringSnapshot   = "storing-snap"
  // format: ON

  val PersistencePhaseKey = "persistencePhase"
  val PersistenceIdKey = "persistenceId"

  // MDC is cleared (if used) from aroundReceive in ActorAdapter after processing each message,
  // but important to call `context.log` to mark MDC as used
  def setMdc(persistenceId: PersistenceId, phase: String): Unit = {
    MDC.put(PersistenceIdKey, persistenceId.id)
    MDC.put(PersistencePhaseKey, phase)
  }

}
