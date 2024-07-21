/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.journal.leveldb

import scala.concurrent.duration.FiniteDuration
import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi
import pekko.persistence.JournalProtocol.RecoverySuccess
import pekko.persistence.JournalProtocol.ReplayMessages
import pekko.persistence.JournalProtocol.ReplayMessagesFailure
import pekko.persistence.JournalProtocol.ReplayedMessage
import pekko.persistence.Persistence
import pekko.persistence.journal.leveldb.LeveldbJournal
import pekko.persistence.journal.leveldb.LeveldbJournal.EventAppended
import pekko.persistence.query.EventEnvelope
import pekko.persistence.query.Sequence
import pekko.persistence.query.journal.leveldb.EventsByPersistenceIdStage.Continue
import pekko.stream.Attributes
import pekko.stream.Materializer
import pekko.stream.Outlet
import pekko.stream.SourceShape
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.OutHandler
import pekko.stream.stage.TimerGraphStageLogicWithLogging

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object EventsByPersistenceIdStage {
  case object Continue
}

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] class EventsByPersistenceIdStage(
    persistenceId: String,
    fromSequenceNr: Long,
    initialToSequenceNr: Long,
    maxBufSize: Int,
    writeJournalPluginId: String,
    refreshInterval: Option[FiniteDuration],
    mat: Materializer)
    extends GraphStage[SourceShape[EventEnvelope]] {
  val out: Outlet[EventEnvelope] = Outlet("EventsByPersistenceIdSource")
  override def shape: SourceShape[EventEnvelope] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogicWithLogging(shape) with OutHandler with Buffer[EventEnvelope] {
      override def doPush(out: Outlet[EventEnvelope], elem: EventEnvelope): Unit = super.push(out, elem)

      val journal: ActorRef = Persistence(mat.system).journalFor(writeJournalPluginId)
      var stageActorRef: ActorRef = null
      var replayInProgress = false
      var outstandingReplay = false

      var nextSequenceNr = fromSequenceNr
      var toSequenceNr = initialToSequenceNr

      override protected def logSource: Class[_] = classOf[EventsByPersistenceIdStage]

      override def preStart(): Unit = {
        stageActorRef = getStageActor(journalInteraction).ref
        refreshInterval.foreach { fd =>
          scheduleWithFixedDelay(Continue, fd, fd)
          journal.tell(LeveldbJournal.SubscribePersistenceId(persistenceId), stageActorRef)
        }
        requestMore()
      }

      private def requestMore(): Unit =
        if (!replayInProgress) {
          val limit = maxBufSize - bufferSize
          if (limit > 0 && nextSequenceNr <= toSequenceNr) {
            replayInProgress = true
            outstandingReplay = false
            val request = ReplayMessages(nextSequenceNr, toSequenceNr, limit, persistenceId, stageActorRef)
            journal ! request
          }
        } else {
          outstandingReplay = true
        }

      override protected def onTimer(timerKey: Any): Unit = {
        requestMore()
        deliverBuf(out)
        maybeCompleteStage()
      }

      private def journalInteraction(in: (ActorRef, Any)): Unit = {
        val (_, msg) = in
        msg match {
          case ReplayedMessage(pr) =>
            buffer(
              EventEnvelope(
                offset = Sequence(pr.sequenceNr),
                persistenceId = pr.persistenceId,
                sequenceNr = pr.sequenceNr,
                event = pr.payload,
                timestamp = pr.timestamp))
            nextSequenceNr = pr.sequenceNr + 1
            deliverBuf(out)

          case RecoverySuccess(highestSeqNr) =>
            replayInProgress = false
            deliverBuf(out)

            if (highestSeqNr < toSequenceNr && isCurrentQuery()) {
              toSequenceNr = highestSeqNr
            }

            log.debug(
              "Replay complete. From sequenceNr {} currentSequenceNr {} toSequenceNr {} buffer size {}",
              fromSequenceNr,
              nextSequenceNr,
              toSequenceNr,
              bufferSize)
            if (bufferEmpty && (nextSequenceNr > toSequenceNr || (nextSequenceNr == fromSequenceNr && isCurrentQuery()))) {
              completeStage()
            } else if (nextSequenceNr < toSequenceNr) {
              // need further requests to the journal
              if (bufferSize < maxBufSize && (isCurrentQuery() || outstandingReplay)) {
                requestMore()
              }
            }

          case ReplayMessagesFailure(cause) =>
            failStage(cause)

          case EventAppended(_) =>
            requestMore()

          case _ => throw new RuntimeException() // compiler exhaustiveness check pleaser
        }
      }

      private def isCurrentQuery(): Boolean = refreshInterval.isEmpty

      private def maybeCompleteStage(): Unit =
        if (bufferEmpty && nextSequenceNr > toSequenceNr) {
          completeStage()
        }

      override def onPull(): Unit = {
        requestMore()
        deliverBuf(out)
        maybeCompleteStage()

      }

      setHandler(out, this)
    }
}
