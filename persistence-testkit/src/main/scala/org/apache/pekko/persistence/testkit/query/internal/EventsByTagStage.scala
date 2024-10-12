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
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.persistence.PersistentRepr
import org.apache.pekko.persistence.journal.Tagged
import org.apache.pekko.persistence.query.EventEnvelope
import org.apache.pekko.persistence.query.Sequence
import org.apache.pekko.persistence.testkit.EventStorage
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin.TagWrite
import org.apache.pekko.stream.stage.GraphStage
import org.apache.pekko.stream.stage.GraphStageLogic
import org.apache.pekko.stream.stage.GraphStageLogicWithLogging
import org.apache.pekko.stream.stage.OutHandler
import org.apache.pekko.stream.Attributes
import org.apache.pekko.stream.Outlet
import org.apache.pekko.stream.SourceShape

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object EventsByTagStage {
  case class State(
      currentTimestamp: Long,
      lastPersistenceId: String,
      lastSequenceNr: Long
  )
}

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] class EventsByTagStage(
    tag: String,
    storage: EventStorage)
    extends GraphStage[SourceShape[EventEnvelope]] {
  import EventsByTagStage._

  val out: Outlet[EventEnvelope] = Outlet("EventsByTagSource")
  override def shape: SourceShape[EventEnvelope] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogicWithLogging(shape) with OutHandler {
      private var state = Option.empty[State]
      private var stageActorRef: ActorRef = null
      override def preStart(): Unit = {
        stageActorRef = getStageActor(receiveNotifications).ref
        materializer.system.eventStream.subscribe(stageActorRef, classOf[PersistenceTestKitPlugin.TagWrite])
      }

      private def receiveNotifications(in: (ActorRef, Any)): Unit = {
        val (_, msg) = in
        (msg, state) match {
          case (TagWrite(tagOfWrite, _), None) if tagOfWrite == tag =>
            tryPush()
          case (TagWrite(tagOfWrite, highestEntry), Some(state))
              if tagOfWrite == tag && (highestEntry.timestamp > state.currentTimestamp || highestEntry.persistenceId > state
                .lastPersistenceId || highestEntry.sequenceNr > state.lastSequenceNr) =>
            tryPush()
          case _ =>
        }
      }

      private def tryPush(): Unit = {
        if (isAvailable(out)) {
          val maybeNextEvent = storage
            .tryReadByTag(tag)
            .sortBy(pr => (pr.timestamp, pr.persistenceId, pr.sequenceNr))
            .find { pr =>
              state match {
                case None =>
                  true
                case Some(state) =>
                  pr.timestamp > state.currentTimestamp || pr.persistenceId > state.lastPersistenceId || pr
                    .sequenceNr > state.lastSequenceNr
              }
            }

          log.debug("tryPush available. State {} event {}", state, maybeNextEvent)

          maybeNextEvent.foreach { pr =>
            push(out,
              EventEnvelope(Sequence(pr.sequenceNr), pr.persistenceId, pr.sequenceNr,
                pr.payload match {
                  case Tagged(payload, _) => payload
                  case payload            => payload
                }, pr.timestamp, pr.metadata))

            state = Some(State(pr.timestamp, pr.persistenceId, pr.sequenceNr))
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
