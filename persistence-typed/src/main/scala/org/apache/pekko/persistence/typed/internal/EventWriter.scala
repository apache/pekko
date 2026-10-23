/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.internal

import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._
import scala.util.Failure
import scala.util.Success

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.Extension
import pekko.actor.typed.ExtensionId
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.actor.typed.scaladsl.adapter.ClassicActorRefOps
import pekko.actor.typed.scaladsl.adapter.TypedActorRefOps
import pekko.annotation.InternalStableApi
import pekko.pattern.StatusReply
import pekko.persistence.AtomicWrite
import pekko.persistence.FilteredPayload
import pekko.persistence.JournalProtocol
import pekko.persistence.Persistence
import pekko.persistence.PersistentRepr
import pekko.persistence.journal.Tagged
import pekko.util.Timeout

/**
 * INTERNAL API
 */
@InternalStableApi
private[pekko] object EventWriterExtension extends ExtensionId[EventWriterExtension] {
  def createExtension(system: ActorSystem[?]): EventWriterExtension = new EventWriterExtension(system)

  def get(system: ActorSystem[?]): EventWriterExtension = apply(system)
}

/**
 * INTERNAL API
 */
@InternalStableApi
private[pekko] class EventWriterExtension(system: ActorSystem[?]) extends Extension {

  private val settings = EventWriter.EventWriterSettings(system)
  private val writersPerJournalId = new ConcurrentHashMap[String, ActorRef[EventWriter.Command]]()

  def writerForJournal(journalId: Option[String]): ActorRef[EventWriter.Command] =
    writersPerJournalId.computeIfAbsent(
      journalId.getOrElse(""),
      { _ =>
        system.systemActorOf(
          EventWriter(journalId.getOrElse(""), settings),
          s"EventWriter-${URLEncoder.encode(journalId.getOrElse("default"), "UTF-8")}")
      })

}

/**
 * INTERNAL API
 */
@InternalStableApi
private[pekko] object EventWriter {

  type SeqNr = Long
  type Pid = String

  object EventWriterSettings {
    def apply(system: ActorSystem[?]): EventWriterSettings = {
      val config = system.settings.config.getConfig("pekko.persistence.typed.event-writer")
      EventWriterSettings(
        maxBatchSize = config.getInt("max-batch-size"),
        askTimeout = config.getDuration("ask-timeout").toScala,
        fillSequenceNumberGaps = config.getBoolean("fill-sequence-number-gaps"),
        latestSequenceNumberCacheCapacity = config.getInt("latest-sequence-number-cache-capacity"))
    }

    /**
     * For binary compatibility, gap filling disabled.
     */
    def apply(maxBatchSize: Int, askTimeout: FiniteDuration): EventWriterSettings =
      new EventWriterSettings(maxBatchSize, askTimeout)

  }
  final case class EventWriterSettings(
      maxBatchSize: Int,
      askTimeout: FiniteDuration,
      fillSequenceNumberGaps: Boolean,
      latestSequenceNumberCacheCapacity: Int) {

    /**
     * For binary compatibility, gap filling disabled.
     */
    def this(maxBatchSize: Int, askTimeout: FiniteDuration) =
      this(maxBatchSize, askTimeout, fillSequenceNumberGaps = false, latestSequenceNumberCacheCapacity = 1000)
  }

  sealed trait Command

  // extends AbstractFunction6 to keep the companion binary compatible with the previous shape
  object Write
      extends scala.runtime.AbstractFunction6[
        Pid,
        SeqNr,
        Any,
        Option[Any],
        Set[String],
        ActorRef[StatusReply[WriteAck]],
        Write] {

    /**
     * For binary compatibility, the event is not a snapshot event.
     */
    def apply(
        persistenceId: Pid,
        sequenceNumber: SeqNr,
        event: Any,
        metadata: Option[Any],
        tags: Set[String],
        replyTo: ActorRef[StatusReply[WriteAck]]): Write =
      new Write(persistenceId, sequenceNumber, event, isSnapshotEvent = false, metadata, tags, replyTo)
  }

  /**
   * @param isSnapshotEvent when `true` gaps before the event will be filled with `FilteredPayload` even
   *                        if `fill-sequence-number-gaps` is disabled
   */
  final case class Write(
      persistenceId: Pid,
      sequenceNumber: SeqNr,
      event: Any,
      isSnapshotEvent: Boolean,
      metadata: Option[Any],
      tags: Set[String],
      replyTo: ActorRef[StatusReply[WriteAck]])
      extends Command {

    /**
     * For binary compatibility, the event is not a snapshot event.
     */
    def this(
        persistenceId: Pid,
        sequenceNumber: SeqNr,
        event: Any,
        metadata: Option[Any],
        tags: Set[String],
        replyTo: ActorRef[StatusReply[WriteAck]]) =
      this(persistenceId, sequenceNumber, event, isSnapshotEvent = false, metadata, tags, replyTo)
  }
  final case class WriteAck(persistenceId: Pid, sequenceNumber: SeqNr)

  private final case class MaxSeqNrForPid(persistenceId: Pid, sequenceNumber: SeqNr, reason: AskMaxSeqNrReason)
      extends Command
  private final case class MaxSeqNrForPidFailed(persistenceId: Pid, reason: AskMaxSeqNrReason, cause: Throwable)
      extends Command

  private sealed trait AskMaxSeqNrReason
  private object AskMaxSeqNrReason {
    final case class WriteFailure(originalErrorDesc: String) extends AskMaxSeqNrReason
    case object FillGaps extends AskMaxSeqNrReason
    case object SnapshotEvent extends AskMaxSeqNrReason
  }

  /**
   * A write waiting for the highest sequence number lookup, with whether it is a snapshot event.
   */
  private final case class PendingLookup(
      repr: PersistentRepr,
      replyTo: ActorRef[StatusReply[WriteAck]],
      isSnapshotEvent: Boolean)

  /**
   * @param duplicateWaiters callers that sent a sequence number that is already pending in
   *                         `waitingForReply` or `waitingForWrite`, they get the same outcome as that write
   */
  private case class StateForPid(
      waitingForReply: Map[SeqNr, (PersistentRepr, ActorRef[StatusReply[WriteAck]])],
      waitingForWrite: Vector[(PersistentRepr, ActorRef[StatusReply[WriteAck]])] = Vector.empty,
      writeErrorHandlingInProgress: Boolean = false,
      currentTransactionId: Int = 0,
      latestSeqNr: SeqNr = -1L,
      usedTimestamp: Long = 0L,
      waitingForSeqNrLookup: Vector[PendingLookup] = Vector.empty,
      duplicateWaiters: Map[SeqNr, Vector[ActorRef[StatusReply[WriteAck]]]] = Map.empty) {
    def idle: Boolean =
      waitingForReply.isEmpty && waitingForWrite.isEmpty

    def seqNrlookupInProgress: Boolean =
      waitingForSeqNrLookup.nonEmpty

    def nextExpectedSeqNr: SeqNr =
      if (waitingForWrite.nonEmpty)
        waitingForWrite.last._1.sequenceNr + 1
      else if (waitingForReply.nonEmpty)
        waitingForReply.keysIterator.max + 1
      else if (latestSeqNr == -1L)
        1L
      else
        latestSeqNr + 1

    def isPending(seqNr: SeqNr): Boolean =
      waitingForReply.contains(seqNr) || waitingForWrite.exists { case (repr, _) => repr.sequenceNr == seqNr }

    /**
     * Replies to the waiting caller of `seqNr` and to its duplicate callers, and forgets the duplicate callers.
     */
    def replyTo(seqNr: SeqNr, reply: StatusReply[WriteAck]): StateForPid = {
      waitingForReply.get(seqNr).foreach { case (_, replyTo) => replyTo ! reply }
      duplicateWaiters.get(seqNr) match {
        case Some(duplicates) =>
          duplicates.foreach(_ ! reply)
          copy(duplicateWaiters = duplicateWaiters - seqNr)
        case None => this
      }
    }

    def waitingForWriteExceedingMaxBatchSize(batchSize: Int): Boolean =
      waitingForWrite.size >= batchSize &&
      waitingForWrite.count { case (repr, _) => repr.payload != FilteredPayload } >= batchSize
  }

  def apply(journalPluginId: String, settings: EventWriterSettings): Behavior[Command] =
    Behaviors
      .supervise(Behaviors.setup[Command] { context =>
        val journal = Persistence(context.system.classicSystem).journalFor(journalPluginId)
        context.log.debug("Event writer for journal [{}] starting up", journalPluginId)
        apply(journal.toTyped, settings)
      })
      .onFailure[Exception](SupervisorStrategy.restart)

  def apply(journal: ActorRef[JournalProtocol.Message], settings: EventWriterSettings): Behavior[Command] =
    Behaviors
      .setup[AnyRef] { context =>
        val writerUuid = UUID.randomUUID().toString

        var perPidWriteState = Map.empty[Pid, StateForPid]
        // duplicate key violations are expected, and would otherwise trip the journal circuit breaker
        var bypassCircuitBreaker = true
        implicit val askTimeout: Timeout = settings.askTimeout
        import settings.fillSequenceNumberGaps

        def journalWriteFailed(cause: Throwable): Throwable =
          new IllegalStateException("Journal write failed", cause)

        def journalWriteRejected(cause: Throwable): Throwable =
          new IllegalStateException("Journal rejected write", cause)

        def sendToJournal(transactionId: Int, reprs: Vector[PersistentRepr]): Unit = {
          if (context.log.isTraceEnabled)
            context.log.traceN(
              "Writing events persistence id [{}], sequence nrs [{}-{}]",
              reprs.head.persistenceId,
              reprs.head.sequenceNr,
              reprs.last.sequenceNr)
          journal ! JournalProtocol.WriteMessages(
            AtomicWrite(reprs) :: Nil,
            context.self.toClassic,
            // Note: we use actorInstanceId to correlate replies from one write request
            actorInstanceId = transactionId,
            bypassCircuitBreaker)
        }

        def handleUpdatedStateForPid(pid: Pid, newStateForPid: StateForPid): Unit = {
          if (newStateForPid.waitingForReply.nonEmpty) {
            // more waiting replyTo before we could batch it or scrap the entry
            perPidWriteState = perPidWriteState.updated(pid, newStateForPid)
          } else {
            if (newStateForPid.waitingForWrite.isEmpty && fillSequenceNumberGaps) {
              perPidWriteState = perPidWriteState.updated(pid, newStateForPid)
              evictLeastRecentlyUsedPids()
            } else if (newStateForPid.waitingForWrite.isEmpty) {
              perPidWriteState = perPidWriteState - pid
            } else {
              // batch waiting for pid
              val newReplyTo = newStateForPid.waitingForWrite.map {
                case (repr, replyTo) => (repr.sequenceNr, (repr, replyTo))
              }.toMap
              val updatedState =
                newStateForPid.copy(
                  newReplyTo,
                  waitingForWrite = Vector.empty,
                  currentTransactionId = newStateForPid.currentTransactionId + 1)

              if (context.log.isTraceEnabled())
                context.log.traceN(
                  "Writing batch of {} events for pid [{}], seq nrs [{}-{}], tx id [{}]",
                  newStateForPid.waitingForWrite.size,
                  pid,
                  newStateForPid.waitingForWrite.head._1.sequenceNr,
                  newStateForPid.waitingForWrite.last._1.sequenceNr,
                  updatedState.currentTransactionId)

              val batch = newStateForPid.waitingForWrite.map { case (repr, _) => repr }
              sendToJournal(updatedState.currentTransactionId, batch)
              perPidWriteState = perPidWriteState.updated(pid, updatedState)

            }
          }
        }

        def askMaxSeqNr(pid: Pid, reason: AskMaxSeqNrReason): Unit = {
          context.log.trace("Request highest sequence number for pid [{}]", pid)
          context.ask(
            journal,
            (replyTo: ActorRef[JournalProtocol.Response]) =>
              JournalProtocol.ReplayMessages(0L, 0L, 1L, pid, replyTo.toClassic)) {
            case Success(JournalProtocol.RecoverySuccess(highestSequenceNr)) =>
              MaxSeqNrForPid(pid, highestSequenceNr, reason)
            case Success(JournalProtocol.ReplayMessagesFailure(cause)) =>
              MaxSeqNrForPidFailed(pid, reason, cause)
            case Failure(cause) =>
              MaxSeqNrForPidFailed(pid, reason, cause)
            case Success(unexpected) =>
              throw new IllegalArgumentException(
                s"Got unexpected reply from journal ${unexpected.getClass} for pid $pid. " +
                s"original reason: $reason")
          }
        }

        def evictLeastRecentlyUsedPids(): Unit = {
          import settings.latestSequenceNumberCacheCapacity
          val accumulationFactor = 1.1
          if (perPidWriteState.size >= latestSequenceNumberCacheCapacity * accumulationFactor) {
            val idleEntries =
              perPidWriteState.iterator.filter {
                case (_, stateForPid) => stateForPid.idle && stateForPid.waitingForSeqNrLookup.isEmpty
              }.toVector

            if (idleEntries.size >= latestSequenceNumberCacheCapacity * accumulationFactor) {
              val pidsToRemove =
                idleEntries
                  .sortBy {
                    case (_, stateForPid) => stateForPid.usedTimestamp
                  }
                  .take(idleEntries.size - latestSequenceNumberCacheCapacity)
                  .map { case (pid, _) => pid }

              if (context.log.isTraceEnabled)
                context.log.traceN(
                  "Evicted cache from [{}] to [{}], persistence ids [{}]",
                  idleEntries.size,
                  idleEntries.size - pidsToRemove.size,
                  pidsToRemove.mkString(", "))

              perPidWriteState --= pidsToRemove
            }
          }
        }

        def handleJournalResponse(response: JournalProtocol.Response): Behavior[AnyRef] =
          response match {
            case JournalProtocol.WriteMessageSuccess(message, transactionId) =>
              bypassCircuitBreaker = true
              val pid = message.persistenceId
              val sequenceNr = message.sequenceNr
              perPidWriteState.get(pid) match {
                case None =>
                  context.log.debugN(
                    "Got write success reply for event with no previous state for pid, ignoring (pid [{}], seq nr [{}], tx id [{}])",
                    pid,
                    sequenceNr,
                    transactionId)
                case Some(stateForPid) =>
                  if (transactionId == stateForPid.currentTransactionId) {
                    stateForPid.waitingForReply.get(sequenceNr) match {
                      case None =>
                        context.log.debugN(
                          "Got write reply for event with no waiting request for seq nr, ignoring (pid [{}], seq nr [{}], tx id [{}])",
                          pid,
                          sequenceNr,
                          transactionId)
                      case Some(_) =>
                        context.log.trace2(
                          "Successfully wrote event persistence id [{}], sequence nr [{}]",
                          pid,
                          sequenceNr)
                        val newState = stateForPid
                          .replyTo(sequenceNr, StatusReply.success(WriteAck(pid, sequenceNr)))
                          .copy(
                            waitingForReply = stateForPid.waitingForReply - sequenceNr,
                            latestSeqNr = sequenceNr,
                            usedTimestamp = System.currentTimeMillis())
                        handleUpdatedStateForPid(pid, newState)
                    }
                  } else {
                    if (context.log.isTraceEnabled) {
                      context.log.traceN(
                        "Got reply for old tx id [{}] (current [{}]) for pid [{}], ignoring",
                        transactionId,
                        stateForPid.currentTransactionId,
                        pid)
                    }
                  }
              }
              Behaviors.same

            case JournalProtocol.WriteMessageFailure(message, error, transactionId) =>
              val pid = message.persistenceId
              val sequenceNr = message.sequenceNr
              perPidWriteState.get(pid) match {
                case None =>
                  context.log.debugN(
                    "Got write error reply for event with no previous state for pid, ignoring (pid [{}], seq nr [{}], tx id [{}])",
                    pid,
                    sequenceNr,
                    transactionId)
                case Some(state) =>
                  // write failure could be re-delivery, we need to check
                  state.waitingForReply.get(sequenceNr) match {
                    case None =>
                      context.log.debugN(
                        "Got write error reply for event with no waiting request for seq nr, ignoring (pid [{}], seq nr [{}], tx id [{}])",
                        pid,
                        sequenceNr,
                        transactionId)
                    case Some(_) =>
                      // quite likely a re-delivery of already persisted events, the whole batch will fail
                      // check highest seqnr and see if we can ack events
                      if (!state.writeErrorHandlingInProgress && state.currentTransactionId == transactionId) {
                        perPidWriteState =
                          perPidWriteState.updated(pid, state.copy(writeErrorHandlingInProgress = true))
                        askMaxSeqNr(pid, AskMaxSeqNrReason.WriteFailure(error.getMessage))
                      } else {
                        context.log.traceN(
                          "Ignoring failure for pid [{}], seq nr [{}], tx id [{}], since write error handling already in progress or old tx id (current tx id [{}])",
                          pid,
                          sequenceNr,
                          transactionId,
                          state.currentTransactionId)
                      }
                  }
              }
              Behaviors.same

            case JournalProtocol.WriteMessageRejected(message, cause, transactionId) =>
              val pid = message.persistenceId
              val sequenceNr = message.sequenceNr
              perPidWriteState.get(pid) match {
                case Some(state)
                    if transactionId == state.currentTransactionId && state.waitingForReply.contains(sequenceNr) =>
                  // the journal rejected the write before storing it, e.g. it could not serialize the event
                  // or a FilteredPayload gap filler, so all events of the atomic write are rejected
                  context.log.warnN(
                    "Journal rejected write of event persistence id [{}], sequence nr [{}]: {}",
                    pid,
                    sequenceNr,
                    cause.getMessage)
                  val newState = state.replyTo(sequenceNr, StatusReply.error(journalWriteRejected(cause)))
                  handleUpdatedStateForPid(pid, newState.copy(waitingForReply = newState.waitingForReply - sequenceNr))
                case _ =>
                  context.log.debugN(
                    "Got write rejection for event with no waiting request, ignoring (pid [{}], seq nr [{}], tx id [{}])",
                    pid,
                    sequenceNr,
                    transactionId)
              }
              Behaviors.same

            case _ =>
              // ignore all other journal protocol messages
              Behaviors.same
          }

        def handleWrite(
            repr: PersistentRepr,
            replyTo: ActorRef[StatusReply[WriteAck]],
            isSnapshotEvent: Boolean,
            calledAfterMaxSeqNr: Boolean): Unit = {
          val persistenceId = repr.persistenceId
          val sequenceNumber = repr.sequenceNr
          val newStateForPid =
            perPidWriteState.get(persistenceId) match {
              case None =>
                if ((fillSequenceNumberGaps || isSnapshotEvent) && sequenceNumber != 1L) {
                  val reason = if (isSnapshotEvent) AskMaxSeqNrReason.SnapshotEvent else AskMaxSeqNrReason.FillGaps
                  askMaxSeqNr(persistenceId, reason)
                  StateForPid(
                    waitingForReply = Map.empty,
                    waitingForSeqNrLookup = Vector(PendingLookup(repr, replyTo, isSnapshotEvent)))
                } else {
                  sendToJournal(1, Vector(repr))
                  StateForPid(Map((repr.sequenceNr, (repr, replyTo))), currentTransactionId = 1)
                }

              case Some(state) =>
                val expectedSeqNr =
                  if (fillSequenceNumberGaps || isSnapshotEvent) state.nextExpectedSeqNr
                  else repr.sequenceNr

                if (state.seqNrlookupInProgress) {
                  context.log.trace2(
                    "Seq nr lookup in progress for persistence id [{}], adding sequence nr [{}] to pending",
                    persistenceId,
                    sequenceNumber)
                  state.copy(waitingForSeqNrLookup =
                    state.waitingForSeqNrLookup :+ PendingLookup(repr, replyTo, isSnapshotEvent))
                } else if (sequenceNumber == expectedSeqNr) {
                  if (state.idle) {
                    sendToJournal(state.currentTransactionId + 1, Vector(repr))
                    state.copy(
                      waitingForReply = Map((repr.sequenceNr, (repr, replyTo))),
                      currentTransactionId = state.currentTransactionId + 1)
                  } else {
                    // write in progress for pid, add write to batch and perform once current write completes
                    if (state.waitingForWriteExceedingMaxBatchSize(settings.maxBatchSize)) {
                      replyTo ! StatusReply.error(
                        s"Max batch reached for pid $persistenceId, at most ${settings.maxBatchSize} writes for " +
                        "the same pid may be in flight at the same time")
                      state
                    } else {
                      context.log.trace2(
                        "Writing event in progress for persistence id [{}], adding sequence nr [{}] to batch",
                        persistenceId,
                        sequenceNumber)
                      state.copy(waitingForWrite = state.waitingForWrite :+ ((repr, replyTo)))
                    }
                  }
                } else if (sequenceNumber < expectedSeqNr) {
                  if (state.isPending(sequenceNumber)) {
                    // not durable yet, reply when the outcome of the pending write is known
                    context.log.trace2(
                      "Duplicate of pending seq nr [{}] for persistence id [{}]",
                      sequenceNumber,
                      persistenceId)
                    state.copy(duplicateWaiters = state.duplicateWaiters.updated(
                      sequenceNumber,
                      state.duplicateWaiters.getOrElse(sequenceNumber, Vector.empty) :+ replyTo))
                  } else {
                    context.log.trace2("Duplicate seq nr [{}] for persistence id [{}]", sequenceNumber, persistenceId)
                    replyTo ! StatusReply.success(WriteAck(persistenceId, sequenceNumber)) // duplicate
                    state
                  }
                } else { // sequenceNumber > expectedSeqNr
                  require(
                    fillSequenceNumberGaps || isSnapshotEvent,
                    s"Unexpected sequence number gap, expected [$expectedSeqNr], received [$sequenceNumber]. " +
                    "Enable pekko.persistence.typed.event-writer.fill-sequence-number-gaps config if gaps are " +
                    "expected and should be filled with FilteredPayload.")

                  if (calledAfterMaxSeqNr || !state.idle) {
                    // here we can trust that the expectedSeqNr is correct and don't have to lookup max
                    val fillRepr = (expectedSeqNr until sequenceNumber).map { n =>
                      PersistentRepr(
                        FilteredPayload,
                        persistenceId = persistenceId,
                        sequenceNr = n,
                        manifest = "", // adapters would be on the producing side, already applied
                        writerUuid = writerUuid,
                        sender = pekko.actor.ActorRef.noSender)
                    }.toVector
                    context.log.debugN(
                      "Detected sequence nr gap{} [{}-{}] for persistence id [{}]. Filling with FilteredPayload.",
                      if (isSnapshotEvent) " for snapshot event" else "",
                      fillRepr.head.sequenceNr,
                      fillRepr.last.sequenceNr,
                      persistenceId)
                    val ignoreRef = context.system.ignoreRef
                    if (state.idle) {
                      sendToJournal(state.currentTransactionId + 1, fillRepr :+ repr)
                      val newWaitingForReply =
                        (fillRepr.map(r => r.sequenceNr -> (r -> ignoreRef)) :+
                        (repr.sequenceNr -> (repr -> replyTo))).toMap
                      state.copy(
                        waitingForReply = newWaitingForReply,
                        currentTransactionId = state.currentTransactionId + 1)
                    } else {
                      if (context.log.isTraceEnabled)
                        context.log.traceN(
                          "Writing event in progress for persistence id [{}], adding sequence nrs [{}-{}] to batch",
                          persistenceId,
                          fillRepr.head.sequenceNr,
                          sequenceNumber)
                      val newWaitingForWrite = fillRepr.map(_ -> ignoreRef) :+ (repr -> replyTo)
                      state.copy(waitingForWrite = state.waitingForWrite ++ newWaitingForWrite)
                    }
                  } else {
                    // No pending writes or we haven't just looked up latest sequence nr.
                    // The reason we don't trust a previous state.latestSeqNr is because another EventWriter
                    // on another node might have been taking writes since we retrieved it.
                    val reason = if (isSnapshotEvent) AskMaxSeqNrReason.SnapshotEvent else AskMaxSeqNrReason.FillGaps
                    askMaxSeqNr(persistenceId, reason)
                    context.log.trace2(
                      "Seq nr lookup needed for persistence id [{}], adding sequence nr [{}] to pending",
                      persistenceId,
                      sequenceNumber)
                    state.copy(waitingForSeqNrLookup =
                      state.waitingForSeqNrLookup :+ PendingLookup(repr, replyTo, isSnapshotEvent))
                  }

                }
            }
          perPidWriteState = perPidWriteState.updated(persistenceId, newStateForPid)
        }

        Behaviors.receiveMessage {
          case Write(persistenceId, sequenceNumber, event, isSnapshotEvent, metadata, tags, replyTo) =>
            val payload = if (tags.isEmpty) event else Tagged(event, tags)
            val repr = PersistentRepr(
              payload,
              persistenceId = persistenceId,
              sequenceNr = sequenceNumber,
              manifest = "", // adapters would be on the producing side, already applied
              writerUuid = writerUuid,
              sender = pekko.actor.ActorRef.noSender)

            val reprWithMeta = metadata match {
              case Some(meta) => repr.withMetadata(meta)
              case _          => repr
            }

            handleWrite(reprWithMeta, replyTo, isSnapshotEvent, calledAfterMaxSeqNr = false)
            Behaviors.same

          case MaxSeqNrForPid(pid, maxSeqNr, AskMaxSeqNrReason.WriteFailure(errorDesc)) =>
            bypassCircuitBreaker = true
            // write failed, so we looked up the maxSeqNr to detect if it was duplicate events, already in journal
            perPidWriteState.get(pid) match {
              case None =>
                context.log.debug2(
                  "Got max seq nr with no waiting previous state for pid, ignoring (pid [{}], original error desc: {})",
                  pid,
                  errorDesc)
              case Some(state) =>
                val sortedSeqs = state.waitingForReply.keys.toSeq.sorted
                val (alreadyInJournal, needsWrite) = sortedSeqs.partition(seqNr => seqNr <= maxSeqNr)
                if (alreadyInJournal.isEmpty) {
                  // error was not about duplicates
                  val stateAfterReplies = sortedSeqs.foldLeft(state) { (state, seqNr) =>
                    state.replyTo(seqNr, StatusReply.error("Journal write failed"))
                  }
                  context.log.warnN(
                    "Failed writing event batch persistence id [{}], sequence nr [{}-{}]: {}",
                    pid,
                    sortedSeqs.head,
                    sortedSeqs.last,
                    errorDesc)
                  val newState =
                    stateAfterReplies.copy(waitingForReply = Map.empty, writeErrorHandlingInProgress = false)
                  handleUpdatedStateForPid(pid, newState)
                } else {
                  // ack all already written
                  val stateAfterWritten = alreadyInJournal
                    .foldLeft(state) { (state, seqNr) =>
                      val newState = state.replyTo(seqNr, StatusReply.success(WriteAck(pid, seqNr)))
                      newState.copy(waitingForReply = newState.waitingForReply - seqNr)
                    }
                    .copy(writeErrorHandlingInProgress = false)
                  if (needsWrite.isEmpty) {
                    handleUpdatedStateForPid(pid, stateAfterWritten)
                  } else {
                    // retrigger write for those left if any, note that we do a
                    val reprsToRewrite =
                      stateAfterWritten.waitingForReply.values.map { case (repr, _) => repr }.toVector
                    if (context.log.isDebugEnabled())
                      context.log.debugN(
                        "Partial batch was duplicates, re-triggering write of persistence id [{}], sequence nr [{}-{}]",
                        pid,
                        reprsToRewrite.head.sequenceNr,
                        reprsToRewrite.last.sequenceNr)
                    // Not going via batch/handleUpdatedStateForPid here because adding partial
                    // failure to current waiting could go over batch size limit
                    val partialRewriteState =
                      stateAfterWritten.copy(currentTransactionId = stateAfterWritten.currentTransactionId + 1)
                    sendToJournal(partialRewriteState.currentTransactionId, reprsToRewrite)
                    handleUpdatedStateForPid(pid, partialRewriteState)
                  }

                }

            }
            Behaviors.same

          case MaxSeqNrForPid(pid, maxSeqNr, AskMaxSeqNrReason.FillGaps | AskMaxSeqNrReason.SnapshotEvent) =>
            bypassCircuitBreaker = true
            perPidWriteState.get(pid) match {
              case None =>
                context.log.debug("Got max seq nr with no waiting previous state for pid, ignoring (pid [{}])", pid)
              case Some(state) =>
                context.log.debug("Retrieved max seq nr [{}] for pid [{}]", maxSeqNr, pid)
                val waiting = state.waitingForSeqNrLookup
                perPidWriteState = perPidWriteState.updated(
                  pid,
                  state.copy(latestSeqNr = maxSeqNr, waitingForSeqNrLookup = Vector.empty))
                // each queued write keeps its own snapshot event flag, the lookup reason only
                // belongs to the write that triggered it
                waiting.foreach { pending =>
                  handleWrite(pending.repr, pending.replyTo, pending.isSnapshotEvent, calledAfterMaxSeqNr = true)
                }
            }
            Behaviors.same

          case MaxSeqNrForPidFailed(pid, reason, cause) =>
            // journal seems to have real problems, let the circuit breaker protect it until next successful write
            bypassCircuitBreaker = false
            def errorDescInLog = reason match {
              case AskMaxSeqNrReason.WriteFailure(errorDesc) => s", original error desc: $errorDesc"
              case _                                         => ""
            }
            perPidWriteState.get(pid) match {
              case None =>
                context.log.debugN(
                  "Got max seq nr (failed) with no waiting previous state for persistence id [{}]{}: {}",
                  pid,
                  errorDescInLog,
                  cause.getMessage)
              case Some(state) =>
                // fail the writes waiting for a journal reply and those waiting for this lookup
                val failedSeqNrs =
                  (state.waitingForReply.keys ++ state.waitingForSeqNrLookup.map(_.repr.sequenceNr)).toVector.sorted
                val failure = StatusReply.error[WriteAck](journalWriteFailed(cause))
                val stateAfterReplies =
                  state.waitingForReply.keys.foldLeft(state)((state, seqNr) => state.replyTo(seqNr, failure))
                state.waitingForSeqNrLookup.foreach(_.replyTo ! failure)
                if (failedSeqNrs.nonEmpty)
                  context.log.warnN(
                    "Failed max seq nr, and therefore failed writing events persistence id [{}], sequence nrs [{}]{}: {}",
                    pid,
                    failedSeqNrs.mkString(", "),
                    errorDescInLog,
                    cause.getMessage)
                val newState = stateAfterReplies.copy(
                  waitingForReply = Map.empty,
                  waitingForSeqNrLookup = Vector.empty,
                  writeErrorHandlingInProgress = false)
                handleUpdatedStateForPid(pid, newState)
            }
            Behaviors.same

          case response: JournalProtocol.Response => handleJournalResponse(response)

          case unexpected =>
            context.log.warn("Unexpected message sent to EventWriter [{}], ignored", unexpected.getClass)
            Behaviors.same

        }
      }
      .narrow[Command]

}
