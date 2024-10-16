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

package org.apache.pekko.persistence.testkit.query.internal

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi
import pekko.persistence.Persistence
import pekko.persistence.query.typed
import pekko.persistence.query.Sequence
import pekko.persistence.testkit.EventStorage
import pekko.persistence.testkit.PersistenceTestKitPlugin.SliceWrite
import pekko.persistence.typed.PersistenceId
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.GraphStageLogicWithLogging
import pekko.stream.stage.OutHandler
import pekko.stream.Attributes
import pekko.stream.Outlet
import pekko.stream.SourceShape

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object EventsBySliceStage {
  // PersistenceTestKitPlugin increments timestamp for each atomic write,
  // which can only contain a single persistence ID,
  // so we only need to track timestamp and sequence number within state,
  // because same timestamp will not have multiple persistence IDs.
  case class State(
      currentTimestamp: Long,
      lastSequenceNr: Long
  ) {
    def isAfter(timestamp: Long, sequenceNr: Long): Boolean = {
      timestamp > currentTimestamp || (timestamp == currentTimestamp && sequenceNr > lastSequenceNr)
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] class EventsBySliceStage[Event](
    entityType: String,
    minSlice: Int,
    maxSlice: Int,
    storage: EventStorage,
    persistence: Persistence
) extends GraphStage[SourceShape[typed.EventEnvelope[Event]]] {
  import EventsBySliceStage._

  val out: Outlet[typed.EventEnvelope[Event]] = Outlet("EventsByTagSource")
  override def shape: SourceShape[typed.EventEnvelope[Event]] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogicWithLogging(shape) with OutHandler {
      private var state = Option.empty[State]
      private var stageActorRef: ActorRef = null
      override def preStart(): Unit = {
        stageActorRef = getStageActor(receiveNotifications).ref
        materializer.system.eventStream.subscribe(stageActorRef, classOf[SliceWrite])
      }

      private def shouldFilter(persistenceId: String): Boolean = {
        val slice = persistence.sliceForPersistenceId(persistenceId)
        PersistenceId.extractEntityType(persistenceId) == entityType && slice >= minSlice && slice <= maxSlice
      }

      private def receiveNotifications(in: (ActorRef, Any)): Unit = {
        val (_, msg) = in
        (msg, state) match {
          case (SliceWrite(persistenceId, timestamp, highestSequenceNr), maybeState)
              if shouldFilter(persistenceId) && maybeState.forall(_.isAfter(timestamp, highestSequenceNr)) =>
            tryPush()
          case _ =>
        }
      }

      private def tryPush(): Unit = {
        if (isAvailable(out)) {
          val maybeNextEvent = storage.tryRead(entityType, repr => shouldFilter(repr.persistenceId))
            .sortBy(pr => (pr.timestamp, pr.sequenceNr))
            .find { pr =>
              state.forall(_.isAfter(pr.timestamp, pr.sequenceNr))
            }

          log.debug("tryPush available. State {} event {}", state, maybeNextEvent)

          maybeNextEvent.foreach { pr =>
            val slice = persistence.sliceForPersistenceId(pr.persistenceId)
            push(out,
              new typed.EventEnvelope[Event](Sequence(pr.sequenceNr), pr.persistenceId, pr.sequenceNr,
                Some(pr.payload.asInstanceOf[Event]), pr.timestamp, pr.metadata, entityType, slice))

            state = Some(State(pr.timestamp, pr.sequenceNr))
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
