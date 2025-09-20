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

package org.apache.pekko.persistence.typed

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import pekko.actor.typed.{ ActorRef, Behavior }
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplicatedEventSourcing }
import pekko.serialization.jackson.CborSerializable

import org.scalatest.concurrent.Eventually
import org.scalatest.wordspec.AnyWordSpecLike

object ReplicationIllegalAccessSpec {

  val R1 = ReplicaId("R1")
  val R2 = ReplicaId("R1")
  val AllReplicas = Set(R1, R2)

  sealed trait Command
  case class AccessInCommandHandler(replyTo: ActorRef[Thrown]) extends Command
  case class AccessInPersistCallback(replyTo: ActorRef[Thrown]) extends Command

  case class Thrown(exception: Option[Throwable])

  case class State(all: List[String]) extends CborSerializable

  def apply(entityId: String, replica: ReplicaId): Behavior[Command] = {
    ReplicatedEventSourcing.commonJournalConfig(
      ReplicationId("IllegalAccessSpec", entityId, replica),
      AllReplicas,
      PersistenceTestKitReadJournal.Identifier)(replicationContext =>
      EventSourcedBehavior[Command, String, State](
        replicationContext.persistenceId,
        State(Nil),
        (_, command) =>
          command match {
            case AccessInCommandHandler(replyTo) =>
              val exception =
                try {
                  replicationContext.origin
                  None
                } catch {
                  case t: Throwable =>
                    Some(t)
                }
              replyTo ! Thrown(exception)
              Effect.none
            case AccessInPersistCallback(replyTo) =>
              Effect.persist("cat").thenRun { _ =>
                val exception =
                  try {
                    replicationContext.concurrent
                    None
                  } catch {
                    case t: Throwable =>
                      Some(t)
                  }
                replyTo ! Thrown(exception)
              }
          },
        (state, event) => state.copy(all = event :: state.all)))
  }

}

class ReplicationIllegalAccessSpec
    extends ScalaTestWithActorTestKit(PersistenceTestKitPlugin.config)
    with AnyWordSpecLike
    with LogCapturing
    with Eventually {
  import ReplicationIllegalAccessSpec._
  "ReplicatedEventSourcing" should {
    "detect illegal access to context in command handler" in {
      val probe = createTestProbe[Thrown]()
      val ref = spawn(ReplicationIllegalAccessSpec("id1", R1))
      ref ! AccessInCommandHandler(probe.ref)
      val thrown: Throwable = probe.expectMessageType[Thrown].exception.get
      thrown.getMessage should include("from the event handler")
    }
    "detect illegal access to context in persist thenRun" in {
      val probe = createTestProbe[Thrown]()
      val ref = spawn(ReplicationIllegalAccessSpec("id1", R1))
      ref ! AccessInPersistCallback(probe.ref)
      val thrown: Throwable = probe.expectMessageType[Thrown].exception.get
      thrown.getMessage should include("from the event handler")
    }
    "detect illegal access in the factory" in {
      val exception = intercept[UnsupportedOperationException] {
        ReplicatedEventSourcing.commonJournalConfig(
          ReplicationId("IllegalAccessSpec", "id2", R1),
          AllReplicas,
          PersistenceTestKitReadJournal.Identifier) { replicationContext =>
          replicationContext.origin
          ???
        }
      }
      exception.getMessage should include("from the event handler")
    }
  }
}
