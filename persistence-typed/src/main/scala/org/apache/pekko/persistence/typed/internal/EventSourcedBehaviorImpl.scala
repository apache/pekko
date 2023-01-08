/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.internal

import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

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
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.annotation._
import pekko.persistence.JournalProtocol
import pekko.persistence.Recovery
import pekko.persistence.RecoveryPermitter
import pekko.persistence.SnapshotProtocol
import pekko.persistence.journal.Tagged
import pekko.persistence.typed.DeleteEventsCompleted
import pekko.persistence.typed.DeleteEventsFailed
import pekko.persistence.typed.DeleteSnapshotsCompleted
import pekko.persistence.typed.DeleteSnapshotsFailed
import pekko.persistence.typed.DeletionTarget
import pekko.persistence.typed.EventAdapter
import pekko.persistence.typed.NoOpEventAdapter
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.PublishedEvent
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.SnapshotAdapter
import pekko.persistence.typed.SnapshotCompleted
import pekko.persistence.typed.SnapshotFailed
import pekko.persistence.typed.SnapshotSelectionCriteria
import pekko.persistence.typed.scaladsl.RetentionCriteria
import pekko.persistence.typed.scaladsl._
import pekko.persistence.typed.scaladsl.{ Recovery => TypedRecovery }
import pekko.util.ConstantFun
import pekko.util.unused
import org.slf4j.LoggerFactory

@InternalApi
private[pekko] object EventSourcedBehaviorImpl {

  object WriterIdentity {

    // ok to wrap around (2*Int.MaxValue restarts will not happen within a journal roundtrip)
    private[pekko] val instanceIdCounter = new AtomicInteger(1)

    def newIdentity(): WriterIdentity = {
      val instanceId: Int = WriterIdentity.instanceIdCounter.getAndIncrement()
      val writerUuid: String = UUID.randomUUID.toString
      WriterIdentity(instanceId, writerUuid)
    }
  }
  final case class WriterIdentity(instanceId: Int, writerUuid: String)

  /**
   * Used by EventSourcedBehaviorTestKit to retrieve the `persistenceId`.
   */
  final case class GetPersistenceId(replyTo: ActorRef[PersistenceId]) extends Signal

  /**
   * Used by EventSourcedBehaviorTestKit to retrieve the state.
   * Can't be a Signal because those are not stashed.
   */
  final case class GetState[State](replyTo: ActorRef[GetStateReply[State]]) extends InternalProtocol

  /**
   * Used to send a state being `null` as an Actor message
   */
  final case class GetStateReply[State](currentState: State)

  /**
   * Used to start the replication stream at the correct sequence number
   */
  final case class GetSeenSequenceNr(replica: ReplicaId, replyTo: ActorRef[Long]) extends InternalProtocol

}

@InternalApi
private[pekko] final case class EventSourcedBehaviorImpl[Command, Event, State](
    persistenceId: PersistenceId,
    emptyState: State,
    commandHandler: EventSourcedBehavior.CommandHandler[Command, Event, State],
    eventHandler: EventSourcedBehavior.EventHandler[State, Event],
    loggerClass: Class[_],
    journalPluginId: Option[String] = None,
    snapshotPluginId: Option[String] = None,
    tagger: Event => Set[String] = (_: Event) => Set.empty[String],
    eventAdapter: EventAdapter[Event, Any] = NoOpEventAdapter.instance[Event],
    snapshotAdapter: SnapshotAdapter[State] = NoOpSnapshotAdapter.instance[State],
    snapshotWhen: (State, Event, Long) => Boolean = ConstantFun.scalaAnyThreeToFalse,
    recovery: Recovery = Recovery(),
    retention: RetentionCriteria = RetentionCriteria.disabled,
    supervisionStrategy: SupervisorStrategy = SupervisorStrategy.stop,
    override val signalHandler: PartialFunction[(State, Signal), Unit] = PartialFunction.empty,
    replication: Option[ReplicationSetup] = None,
    publishEvents: Boolean = true)
    extends EventSourcedBehavior[Command, Event, State] {

  import EventSourcedBehaviorImpl.WriterIdentity

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
    val settings = EventSourcedSettings(ctx.system, journalPluginId.getOrElse(""), snapshotPluginId.getOrElse(""))

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
      case (_, SnapshotCompleted(meta)) =>
        internalLogger().debug("Save snapshot successful, snapshot metadata [{}].", meta)
      case (_, SnapshotFailed(meta, failure)) =>
        internalLogger()
          .error(s"Save snapshot failed, snapshot metadata [$meta] due to: ${failure.getMessage}", failure)
      case (_, DeleteSnapshotsCompleted(DeletionTarget.Individual(meta))) =>
        internalLogger().debug("Persistent snapshot [{}] deleted successfully.", meta)
      case (_, DeleteSnapshotsCompleted(DeletionTarget.Criteria(criteria))) =>
        internalLogger().debug("Persistent snapshots given criteria [{}] deleted successfully.", criteria)
      case (_, DeleteSnapshotsFailed(DeletionTarget.Individual(meta), failure)) =>
        internalLogger().warn2("Failed to delete snapshot with meta [{}] due to: {}", meta, failure.getMessage)
      case (_, DeleteSnapshotsFailed(DeletionTarget.Criteria(criteria), failure)) =>
        internalLogger().warn2(
          "Failed to delete snapshots given criteria [{}] due to: {}",
          criteria,
          failure.getMessage)
      case (_, DeleteEventsCompleted(toSequenceNr)) =>
        internalLogger().debug("Events successfully deleted to sequence number [{}].", toSequenceNr)
      case (_, DeleteEventsFailed(toSequenceNr, failure)) =>
        internalLogger().warn2(
          "Failed to delete events to sequence number [{}] due to: {}",
          toSequenceNr,
          failure.getMessage)
      case (_, EventSourcedBehaviorImpl.GetPersistenceId(replyTo)) => replyTo ! persistenceId
    }

    // do this once, even if the actor is restarted
    initialize(context.asScala)

    Behaviors
      .supervise {
        Behaviors.setup[Command] { _ =>
          val eventSourcedSetup = new BehaviorSetup(
            ctx.asInstanceOf[ActorContext[InternalProtocol]],
            persistenceId,
            emptyState,
            commandHandler,
            eventHandler,
            WriterIdentity.newIdentity(),
            actualSignalHandler,
            tagger,
            eventAdapter,
            snapshotAdapter,
            snapshotWhen,
            recovery,
            retention,
            holdingRecoveryPermit = false,
            settings = settings,
            stashState = stashState,
            replication = replication,
            publishEvents = publishEvents,
            internalLoggerFactory = () => internalLogger())

          // needs to accept Any since we also can get messages from the journal
          // not part of the user facing Command protocol
          def interceptor: BehaviorInterceptor[Any, InternalProtocol] = new BehaviorInterceptor[Any, InternalProtocol] {

            import BehaviorInterceptor._
            override def aroundReceive(
                ctx: typed.TypedActorContext[Any],
                msg: Any,
                target: ReceiveTarget[InternalProtocol]): Behavior[InternalProtocol] = {
              val innerMsg = msg match {
                case res: JournalProtocol.Response           => InternalProtocol.JournalResponse(res)
                case res: SnapshotProtocol.Response          => InternalProtocol.SnapshotterResponse(res)
                case RecoveryPermitter.RecoveryPermitGranted => InternalProtocol.RecoveryPermitGranted
                case internal: InternalProtocol              => internal // such as RecoveryTickEvent
                case cmd                                     => InternalProtocol.IncomingCommand(cmd.asInstanceOf[Command])
              }
              target(ctx, innerMsg)
            }

            override def aroundSignal(
                ctx: typed.TypedActorContext[Any],
                signal: Signal,
                target: SignalTarget[InternalProtocol]): Behavior[InternalProtocol] = {
              if (signal == PostStop) {
                eventSourcedSetup.cancelRecoveryTimer()
                // clear stash to be GC friendly
                stashState.clearStashBuffers()
              }
              target(ctx, signal)
            }

            override def toString: String = "EventSourcedBehaviorInterceptor"
          }

          Behaviors.intercept(() => interceptor)(RequestingRecoveryPermit(eventSourcedSetup)).narrow
        }

      }
      .onFailure[JournalFailureException](supervisionStrategy)
  }

  @InternalStableApi
  private[pekko] def initialize(@unused context: ActorContext[_]): Unit = ()

  override def receiveSignal(
      handler: PartialFunction[(State, Signal), Unit]): EventSourcedBehavior[Command, Event, State] =
    copy(signalHandler = handler)

  override def withJournalPluginId(id: String): EventSourcedBehavior[Command, Event, State] = {
    require(id != null, "journal plugin id must not be null; use empty string for 'default' journal")
    copy(journalPluginId = if (id != "") Some(id) else None)
  }

  override def withSnapshotPluginId(id: String): EventSourcedBehavior[Command, Event, State] = {
    require(id != null, "snapshot plugin id must not be null; use empty string for 'default' snapshot store")
    copy(snapshotPluginId = if (id != "") Some(id) else None)
  }

  override def withSnapshotSelectionCriteria(
      selection: SnapshotSelectionCriteria): EventSourcedBehavior[Command, Event, State] = {
    copy(recovery = Recovery(selection.toClassic))
  }

  override def snapshotWhen(predicate: (State, Event, Long) => Boolean): EventSourcedBehavior[Command, Event, State] =
    copy(snapshotWhen = predicate)

  override def withRetention(criteria: RetentionCriteria): EventSourcedBehavior[Command, Event, State] =
    copy(retention = criteria)

  override def withTagger(tagger: Event => Set[String]): EventSourcedBehavior[Command, Event, State] =
    copy(tagger = tagger)

  override def eventAdapter(adapter: EventAdapter[Event, _]): EventSourcedBehavior[Command, Event, State] =
    copy(eventAdapter = adapter.asInstanceOf[EventAdapter[Event, Any]])

  override def snapshotAdapter(adapter: SnapshotAdapter[State]): EventSourcedBehavior[Command, Event, State] =
    copy(snapshotAdapter = adapter)

  override def onPersistFailure(
      backoffStrategy: BackoffSupervisorStrategy): EventSourcedBehavior[Command, Event, State] =
    copy(supervisionStrategy = backoffStrategy)

  override def withRecovery(recovery: TypedRecovery): EventSourcedBehavior[Command, Event, State] = {
    copy(recovery = recovery.toClassic)
  }

  override def withEventPublishing(enabled: Boolean): EventSourcedBehavior[Command, Event, State] = {
    copy(publishEvents = enabled)
  }

  override private[pekko] def withReplication(
      context: ReplicationContextImpl): EventSourcedBehavior[Command, Event, State] = {
    copy(
      replication = Some(ReplicationSetup(context.replicationId.replicaId, context.replicasAndQueryPlugins, context)))
  }
}

/** Protocol used internally by the eventsourced behaviors. */
@InternalApi private[pekko] sealed trait InternalProtocol
@InternalApi private[pekko] object InternalProtocol {
  case object RecoveryPermitGranted extends InternalProtocol
  final case class JournalResponse(msg: pekko.persistence.JournalProtocol.Response) extends InternalProtocol
  final case class SnapshotterResponse(msg: pekko.persistence.SnapshotProtocol.Response) extends InternalProtocol
  final case class RecoveryTickEvent(snapshot: Boolean) extends InternalProtocol
  final case class IncomingCommand[C](c: C) extends InternalProtocol

  final case class ReplicatedEventEnvelope[E](event: ReplicatedEvent[E], ack: ActorRef[ReplicatedEventAck.type])
      extends InternalProtocol

}

object ReplicatedEventMetadata {

  /**
   * For a journal supporting Replicated Event Sourcing needing to add test coverage, use this instance as metadata and defer
   * to the built in serializer for serialization format
   */
  def instanceForJournalTest: Any = ReplicatedEventMetadata(ReplicaId("DC-A"), 1L, VersionVector.empty + "DC-A", true)
}

/**
 * @param originReplica Where the event originally was created
 * @param originSequenceNr The original sequenceNr in the origin DC
 * @param version The version with which the event was persisted at the different DC. The same event will have different version vectors
 *                at each location as they are received at different times
 */
@InternalApi
private[pekko] final case class ReplicatedEventMetadata(
    originReplica: ReplicaId,
    originSequenceNr: Long,
    version: VersionVector,
    concurrent: Boolean) // whether when the event handler was executed the event was concurrent

object ReplicatedSnapshotMetadata {

  /**
   * For a snapshot store supporting Replicated Event Sourcing needing to add test coverage, use this instance as metadata and defer
   * to the built in serializer for serialization format
   */
  def instanceForSnapshotStoreTest: Any =
    ReplicatedSnapshotMetadata(
      VersionVector.empty + "DC-B" + "DC-A",
      Map(ReplicaId("DC-A") -> 1L, ReplicaId("DC-B") -> 1L))

}

@InternalApi
private[pekko] final case class ReplicatedSnapshotMetadata(version: VersionVector, seenPerReplica: Map[ReplicaId, Long])

/**
 * An event replicated from a different replica.
 *
 * The version is for when it was persisted at the other replica. At the current replica it will be
 * merged with the current local version.
 */
@InternalApi
private[pekko] final case class ReplicatedEvent[E](
    event: E,
    originReplica: ReplicaId,
    originSequenceNr: Long,
    originVersion: VersionVector)
@InternalApi
private[pekko] case object ReplicatedEventAck

final class ReplicatedPublishedEventMetaData(val replicaId: ReplicaId, private[pekko] val version: VersionVector)

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class PublishedEventImpl(
    persistenceId: PersistenceId,
    sequenceNumber: Long,
    payload: Any,
    timestamp: Long,
    replicatedMetaData: Option[ReplicatedPublishedEventMetaData])
    extends PublishedEvent
    with InternalProtocol {
  import scala.compat.java8.OptionConverters._

  def tags: Set[String] = payload match {
    case t: Tagged => t.tags
    case _         => Set.empty
  }

  def event: Any = payload match {
    case Tagged(event, _) => event
    case _                => payload
  }

  override def withoutTags: PublishedEvent = payload match {
    case Tagged(event, _) => copy(payload = event)
    case _                => this
  }

  override def getReplicatedMetaData: Optional[ReplicatedPublishedEventMetaData] = replicatedMetaData.asJava
}
