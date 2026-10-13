/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.query.internal

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi
import pekko.persistence.Persistence
import pekko.persistence.journal.Tagged
import pekko.persistence.query.TimestampOffset
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.testkit.{ EventStorage, PersistenceTestKitPlugin }
import pekko.persistence.typed.PersistenceId
import pekko.stream.{ Attributes, Outlet, SourceShape }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, GraphStageLogicWithLogging, OutHandler }

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] class TypedEventsByPersistenceIdStage[Event](
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    storage: EventStorage,
    persistence: Persistence)
    extends GraphStage[SourceShape[EventEnvelope[Event]]] {
  val out: Outlet[EventEnvelope[Event]] = Outlet("TypedEventsByPersistenceIdSource")
  override def shape: SourceShape[EventEnvelope[Event]] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogicWithLogging(shape) with OutHandler {
      private var currentSequenceNr = math.max(fromSequenceNr, 1)
      private var stageActorRef: ActorRef = null
      override def preStart(): Unit = {
        stageActorRef = getStageActor(receiveNotifications).ref
        materializer.system.eventStream.subscribe(stageActorRef, classOf[PersistenceTestKitPlugin.Write])
      }

      private def receiveNotifications(in: (ActorRef, Any)): Unit = {
        val (_, msg) = in
        msg match {
          case PersistenceTestKitPlugin.Write(pid, toSequenceNr) if pid == persistenceId =>
            if (toSequenceNr >= currentSequenceNr) {
              tryPush()
            }
          case _ =>
        }
      }

      private def tryPush(): Unit = {
        if (isAvailable(out)) {
          val event = storage.tryRead(persistenceId, currentSequenceNr, currentSequenceNr, 1)
          log.debug("tryPush available. Query for {} {} result {}", currentSequenceNr, currentSequenceNr, event)
          event.headOption match {
            case Some(pr) =>
              val timestamp = Instant.ofEpochMilli(pr.timestamp)
              val readTimestamp = Instant.now().truncatedTo(ChronoUnit.MICROS)
              val slice = persistence.sliceForPersistenceId(persistenceId)
              val entityType = PersistenceId.extractEntityType(persistenceId)
              val tags = pr.payload match {
                case Tagged(_, t) => t
                case _            => Set.empty[String]
              }
              val payload = pr.payload match {
                case Tagged(p, _) => p
                case p            => p
              }
              push(out,
                EventEnvelope(
                  TimestampOffset(timestamp, readTimestamp, Map(pr.persistenceId -> pr.sequenceNr)),
                  pr.persistenceId,
                  pr.sequenceNr,
                  payload.asInstanceOf[Event],
                  pr.timestamp,
                  entityType,
                  slice,
                  filtered = false,
                  source = "",
                  tags = tags))
              if (currentSequenceNr == toSequenceNr) {
                completeStage()
              } else {
                currentSequenceNr += 1
              }
            case None =>
          }
        } else {
          log.debug("tryPush, no demand")
        }
      }

      override def onPull(): Unit = {
        tryPush()
      }

      setHandler(out, this)
    }

  }

}
