/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence.typed.internal

import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.duration.FiniteDuration
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
import pekko.persistence.JournalProtocol
import pekko.persistence.Persistence
import pekko.persistence.PersistentRepr
import pekko.persistence.journal.Tagged
import pekko.util.JavaDurationConverters.JavaDurationOps
import pekko.util.Timeout

/**
 * INTERNAL API
 */
@InternalStableApi
private[pekko] object EventWriterExtension extends ExtensionId[EventWriterExtension] {
  def createExtension(system: ActorSystem[_]): EventWriterExtension = new EventWriterExtension(system)

  def get(system: ActorSystem[_]): EventWriterExtension = apply(system)
}

/**
 * INTERNAL API
 */
@InternalStableApi
private[pekko] class EventWriterExtension(system: ActorSystem[_]) extends Extension {

  private val settings = EventWriter.EventWriterSettings(system)
  private val writersPerJournalId = new ConcurrentHashMap[String, ActorRef[EventWriter.Command]]()

  def writerForJournal(journalId: Option[String]): ActorRef[EventWriter.Command] =
    writersPerJournalId.computeIfAbsent(
      journalId.getOrElse(""), { _ =>
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
    def apply(system: ActorSystem[_]): EventWriterSettings =
      EventWriterSettings(
        maxBatchSize = system.settings.config.getInt("pekko.persistence.typed.event-writer.max-batch-size"),
        askTimeout = system.settings.config.getDuration("pekko.persistence.typed.event-writer.ask-timeout").asScala)

  }
  final case class EventWriterSettings(maxBatchSize: Int, askTimeout: FiniteDuration)

  sealed trait Command
  final case class Write(
      persistenceId: Pid,
      sequenceNumber: SeqNr,
      event: Any,
      metadata: Option[Any],
      tags: Set[String],
      replyTo: ActorRef[StatusReply[WriteAck]])
      extends Command
  final case class WriteAck(persistenceId: Pid, sequenceNumber: SeqNr)

  private case class MaxSeqNrForPid(persistenceId: Pid, sequenceNumber: SeqNr, originalErrorDesc: String)
      extends Command

  private def emptyWaitingForWrite = Vector.empty[(PersistentRepr, ActorRef[StatusReply[WriteAck]])]
  private case class StateForPid(
      waitingForReply: Map[SeqNr, (PersistentRepr, ActorRef[StatusReply[WriteAck]])],
      waitingForWrite: Vector[(PersistentRepr, ActorRef[StatusReply[WriteAck]])] = emptyWaitingForWrite,
      writeErrorHandlingInProgress: Boolean = false,
      currentTransactionId: Int = 0)

  def apply(journalPluginId: String, eventWriterSettings: EventWriterSettings): Behavior[Command] =
    Behaviors
      .supervise(Behaviors.setup[Command] { context =>
        val journal = Persistence(context.system).journalFor(journalPluginId)
        context.log.debug("Event writer for journal [{}] starting up", journalPluginId)
        apply(journal.toTyped, eventWriterSettings)
      })
      .onFailure[Exception](SupervisorStrategy.restart)

  def apply(journal: ActorRef[JournalProtocol.Message], eventWriterSettings: EventWriterSettings): Behavior[Command] =
    Behaviors
      .setup[AnyRef] { context =>
        val writerUuid = UUID.randomUUID().toString

        var perPidWriteState = Map.empty[Pid, StateForPid]
        implicit val askTimeout: Timeout = eventWriterSettings.askTimeout

        def sendToJournal(transactionId: Int, reprs: Vector[PersistentRepr]) = {
          journal ! JournalProtocol.WriteMessages(
            AtomicWrite(reprs) :: Nil,
            context.self.toClassic,
            // Note: we use actorInstanceId to correlate replies from one write request
            actorInstanceId = transactionId)
        }

        def handleUpdatedStateForPid(pid: Pid, newStateForPid: StateForPid): Unit = {
          if (newStateForPid.waitingForReply.nonEmpty) {
            perPidWriteState = perPidWriteState.updated(pid, newStateForPid)
          } else {
            if (newStateForPid.waitingForWrite.isEmpty) {
              perPidWriteState = perPidWriteState - pid
            } else {
              val newReplyTo = newStateForPid.waitingForWrite.map {
                case (repr, replyTo) => (repr.sequenceNr, (repr, replyTo))
              }.toMap
              val updatedState =
                newStateForPid.copy(
                  newReplyTo,
                  emptyWaitingForWrite,
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

        def handleJournalResponse(response: JournalProtocol.Response): Behavior[AnyRef] =
          response match {
            case JournalProtocol.WriteMessageSuccess(message, transactionId) =>
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
                          "Got write error reply for event with no waiting request for seq nr, ignoring (pid [{}], seq nr [{}], tx id [{}])",
                          pid,
                          sequenceNr,
                          transactionId)
                      case Some((_, waiting)) =>
                        context.log.trace2(
                          "Successfully wrote event persistence id [{}], sequence nr [{}]",
                          pid,
                          message.sequenceNr)
                        waiting ! StatusReply.success(WriteAck(pid, sequenceNr))
                        val newState = stateForPid.copy(waitingForReply = stateForPid.waitingForReply - sequenceNr)
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
                  state.waitingForReply.get(sequenceNr) match {
                    case None =>
                      context.log.debugN(
                        "Got write error reply for event with no waiting request for seq nr, ignoring (pid [{}], seq nr [{}], tx id [{}])",
                        pid,
                        sequenceNr,
                        transactionId)
                    case Some(_) =>
                      if (!state.writeErrorHandlingInProgress && state.currentTransactionId == transactionId) {
                        perPidWriteState =
                          perPidWriteState.updated(pid, state.copy(writeErrorHandlingInProgress = true))
                        context.ask(
                          journal,
                          (replyTo: ActorRef[JournalProtocol.Response]) =>
                            JournalProtocol.ReplayMessages(0L, 0L, 1L, pid, replyTo.toClassic)) {
                          case Success(JournalProtocol.RecoverySuccess(highestSequenceNr)) =>
                            MaxSeqNrForPid(pid, highestSequenceNr, error.getMessage)
                          case Success(unexpected) =>
                            throw new IllegalArgumentException(
                              s"Got unexpected reply from journal ${unexpected.getClass} for pid $pid")
                          case Failure(exception) =>
                            throw new RuntimeException(
                              s"Error finding highest sequence number in journal for pid $pid",
                              exception)
                        }
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

            case _ =>
              Behaviors.same
          }

        Behaviors.receiveMessage {
          case Write(persistenceId, sequenceNumber, event, metadata, tags, replyTo) =>
            val payload = if (tags.isEmpty) event else Tagged(event, tags)
            val repr = PersistentRepr(
              payload,
              persistenceId = persistenceId,
              sequenceNr = sequenceNumber,
              manifest = "",
              writerUuid = writerUuid,
              sender = pekko.actor.ActorRef.noSender)

            val reprWithMeta = metadata match {
              case Some(meta) => repr.withMetadata(meta)
              case _          => repr
            }

            val newStateForPid =
              perPidWriteState.get(persistenceId) match {
                case None =>
                  if (context.log.isTraceEnabled)
                    context.log.traceN(
                      "Writing event persistence id [{}], sequence nr [{}], payload {}",
                      persistenceId,
                      sequenceNumber,
                      event)
                  sendToJournal(1, Vector(reprWithMeta))
                  StateForPid(
                    Map((reprWithMeta.sequenceNr, (reprWithMeta, replyTo))),
                    emptyWaitingForWrite,
                    currentTransactionId = 1)
                case Some(state) =>
                  if (state.waitingForWrite.size == eventWriterSettings.maxBatchSize) {
                    replyTo ! StatusReply.error(
                      s"Max batch reached for pid $persistenceId, at most ${eventWriterSettings.maxBatchSize} writes for " +
                      "the same pid may be in flight at the same time")
                    state
                  } else {
                    if (context.log.isTraceEnabled)
                      context.log.traceN(
                        "Writing event in progress for persistence id [{}], adding sequence nr [{}], payload {} to batch",
                        persistenceId,
                        sequenceNumber,
                        event)
                    state.copy(waitingForWrite = state.waitingForWrite :+ ((reprWithMeta, replyTo)))
                  }
              }
            perPidWriteState = perPidWriteState.updated(persistenceId, newStateForPid)
            Behaviors.same

          case MaxSeqNrForPid(pid, maxSeqNr, originalErrorDesc) =>
            perPidWriteState.get(pid) match {
              case None =>
                context.log.debug2(
                  "Got max seq nr with no waiting previous state for pid, ignoring (pid [{}], original error desc: {})",
                  pid,
                  originalErrorDesc)
              case Some(state) =>
                val sortedSeqs = state.waitingForReply.keys.toSeq.sorted
                val (alreadyInJournal, needsWrite) = sortedSeqs.partition(seqNr => seqNr <= maxSeqNr)
                if (alreadyInJournal.isEmpty) {
                  state.waitingForReply.values.foreach {
                    case (_, replyTo) =>
                      replyTo ! StatusReply.error("Journal write failed")
                  }
                  context.log.warnN(
                    "Failed writing event batch persistence id [{}], sequence nr [{}-{}]: {}",
                    pid,
                    sortedSeqs.head,
                    sortedSeqs.last,
                    originalErrorDesc)
                  val newState = state.copy(waitingForReply = Map.empty, writeErrorHandlingInProgress = false)
                  handleUpdatedStateForPid(pid, newState)
                } else {
                  val stateAfterWritten = alreadyInJournal
                    .foldLeft(state) { (state, seqNr) =>
                      val (_, replyTo) = state.waitingForReply(seqNr)
                      replyTo ! StatusReply.success(WriteAck(pid, seqNr))
                      state.copy(waitingForReply = state.waitingForReply - seqNr)
                    }
                    .copy(writeErrorHandlingInProgress = false)
                  if (needsWrite.isEmpty) {
                    handleUpdatedStateForPid(pid, stateAfterWritten)
                  } else {
                    val reprsToRewrite =
                      stateAfterWritten.waitingForReply.values.map { case (repr, _) => repr }.toVector
                    if (context.log.isDebugEnabled())
                      context.log.debugN(
                        "Partial batch was duplicates, re-triggering write of persistence id [{}], sequence nr [{}-{}]",
                        pid,
                        reprsToRewrite.head.sequenceNr,
                        reprsToRewrite.last.sequenceNr)
                    val partialRewriteState =
                      stateAfterWritten.copy(currentTransactionId = stateAfterWritten.currentTransactionId + 1)
                    sendToJournal(partialRewriteState.currentTransactionId, reprsToRewrite)
                    handleUpdatedStateForPid(pid, partialRewriteState)
                  }

                }

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
