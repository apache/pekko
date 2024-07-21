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

package org.apache.pekko.persistence.typed.crdt

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.ReplicationId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.persistence.typed.scaladsl.ReplicatedEventSourcing
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.ReplicationBaseSpec
import pekko.serialization.jackson.CborSerializable

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LwwSpec {

  import ReplicationBaseSpec._

  sealed trait Command
  final case class Update(item: String, timestamp: Long, error: ActorRef[String], latch: Option[CountDownLatch])
      extends Command
  final case class Get(replyTo: ActorRef[Registry]) extends Command

  sealed trait Event extends CborSerializable
  final case class Changed(item: String, timestamp: LwwTime) extends Event

  final case class Registry(item: String, updatedTimestamp: LwwTime) extends CborSerializable

  object LwwRegistry {

    def apply(entityId: String, replica: ReplicaId): Behavior[Command] =
      ReplicatedEventSourcing.commonJournalConfig(
        ReplicationId("LwwRegistrySpec", entityId, replica),
        AllReplicas,
        PersistenceTestKitReadJournal.Identifier) { replicationContext =>
        EventSourcedBehavior[Command, Event, Registry](
          replicationContext.persistenceId,
          Registry("", LwwTime(Long.MinValue, replicationContext.replicaId)),
          (state, command) =>
            command match {
              case Update(s, timestmap, error, maybeLatch) =>
                if (s == "") {
                  error ! "bad value"
                  Effect.none
                } else {
                  maybeLatch.foreach { l =>
                    l.countDown()
                    l.await(10, TimeUnit.SECONDS)
                  }
                  Effect.persist(Changed(s, state.updatedTimestamp.increase(timestmap, replicationContext.replicaId)))
                }
              case Get(replyTo) =>
                replyTo ! state
                Effect.none
            },
          (state, event) =>
            event match {
              case Changed(s, timestamp) =>
                if (timestamp.isAfter(state.updatedTimestamp)) Registry(s, timestamp)
                else state
            })
      }

  }
}

class LwwSpec extends ReplicationBaseSpec {
  import LwwSpec._
  import ReplicationBaseSpec._

  class Setup {
    val entityId = nextEntityId
    val r1 = spawn(LwwRegistry.apply(entityId, R1))
    val r2 = spawn(LwwRegistry.apply(entityId, R2))
    val r1Probe = createTestProbe[String]()
    val r2Probe = createTestProbe[String]()
    val r1GetProbe = createTestProbe[Registry]()
    val r2GetProbe = createTestProbe[Registry]()
  }

  "Lww Replicated Event Sourced Behavior" should {
    "replicate a single event" in new Setup {
      r1 ! Update("a1", 1L, r1Probe.ref, None)
      eventually {
        val probe = createTestProbe[Registry]()
        r2 ! Get(probe.ref)
        probe.expectMessage(Registry("a1", LwwTime(1L, R1)))
      }
    }

    "resolve conflict" in new Setup {
      r1 ! Update("a1", 1L, r1Probe.ref, None)
      r2 ! Update("b1", 2L, r2Probe.ref, None)
      eventually {
        r1 ! Get(r1GetProbe.ref)
        r2 ! Get(r2GetProbe.ref)
        r1GetProbe.expectMessage(Registry("b1", LwwTime(2L, R2)))
        r2GetProbe.expectMessage(Registry("b1", LwwTime(2L, R2)))
      }
    }

    "have deterministic tiebreak when the same time" in new Setup {
      val latch = new CountDownLatch(3)
      r1 ! Update("a1", 1L, r1Probe.ref, Some(latch))
      r2 ! Update("b1", 1L, r2Probe.ref, Some(latch))

      // the commands have arrived in both actors, waiting for the latch,
      // so that the persist of the events will be concurrent
      latch.countDown()
      latch.await(10, TimeUnit.SECONDS)

      // R1 < R2
      eventually {
        r1 ! Get(r1GetProbe.ref)
        r2 ! Get(r2GetProbe.ref)
        r1GetProbe.expectMessage(Registry("a1", LwwTime(1L, R1)))
        r2GetProbe.expectMessage(Registry("a1", LwwTime(1L, R1)))
      }
    }
  }

}
