/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.query.internal
import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi
import pekko.persistence.journal.Tagged
import pekko.persistence.query.{ EventEnvelope, Sequence }
import pekko.persistence.testkit.{ EventStorage, PersistenceTestKitPlugin }
import pekko.stream.{ Attributes, Outlet, SourceShape }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, GraphStageLogicWithLogging, OutHandler }

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] class EventsByPersistenceIdStage(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    storage: EventStorage)
    extends GraphStage[SourceShape[EventEnvelope]] {
  val out: Outlet[EventEnvelope] = Outlet("EventsByPersistenceIdSource")
  override def shape: SourceShape[EventEnvelope] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
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

      private def tryPush(): Unit =
        if (isAvailable(out)) {
          val event = storage.tryRead(persistenceId, currentSequenceNr, currentSequenceNr, 1)
          log.debug("tryPush available. Query for {} {} result {}", currentSequenceNr, currentSequenceNr, event)
          event.headOption match {
            case Some(pr) =>
              push(out,
                EventEnvelope(Sequence(pr.sequenceNr), pr.persistenceId, pr.sequenceNr,
                  pr.payload match {
                    case Tagged(payload, _) => payload
                    case payload            => payload
                  }, pr.timestamp, pr.metadata))
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

      override def onPull(): Unit =
        tryPush()

      setHandler(out, this)
    }

}
