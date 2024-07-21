/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.crdt

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.ReplicationId
import pekko.persistence.typed.crdt.CounterSpec.PlainCounter.{ Decrement, Get, Increment }
import pekko.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplicatedEventSourcing }
import pekko.persistence.typed.{ ReplicaId, ReplicationBaseSpec }

object CounterSpec {

  object PlainCounter {
    sealed trait Command
    case class Get(reply: ActorRef[Long]) extends Command
    case object Increment extends Command
    case object Decrement extends Command
  }

  import ReplicationBaseSpec._

  def apply(
      entityId: String,
      replicaId: ReplicaId,
      snapshotEvery: Long = 100,
      eventProbe: Option[ActorRef[Counter.Updated]] = None) =
    Behaviors.setup[PlainCounter.Command] { context =>
      ReplicatedEventSourcing.commonJournalConfig(
        ReplicationId("CounterSpec", entityId, replicaId),
        AllReplicas,
        PersistenceTestKitReadJournal.Identifier) { ctx =>
        EventSourcedBehavior[PlainCounter.Command, Counter.Updated, Counter](
          ctx.persistenceId,
          Counter.empty,
          (state, command) =>
            command match {
              case PlainCounter.Increment =>
                context.log.info("Increment. Current state {}", state.value)
                Effect.persist(Counter.Updated(1))
              case PlainCounter.Decrement =>
                Effect.persist(Counter.Updated(-1))
              case Get(replyTo) =>
                context.log.info("Get request. {} {}", state.value, state.value.longValue)
                replyTo ! state.value.longValue
                Effect.none
            },
          (counter, event) => {
            eventProbe.foreach(_ ! event)
            counter.applyOperation(event)
          }).snapshotWhen { (_, _, seqNr) =>
          seqNr % snapshotEvery == 0
        }
      }
    }
}

class CounterSpec extends ReplicationBaseSpec {

  import CounterSpec._
  import ReplicationBaseSpec._

  "Replicated entity using CRDT counter" should {
    "replicate" in {
      val id = nextEntityId
      val r1 = spawn(apply(id, R1))
      val r2 = spawn(apply(id, R2))
      val r1Probe = createTestProbe[Long]()
      val r2Probe = createTestProbe[Long]()

      r1 ! Increment
      r1 ! Increment

      eventually {
        r1 ! Get(r1Probe.ref)
        r1Probe.expectMessage(2L)
        r2 ! Get(r2Probe.ref)
        r2Probe.expectMessage(2L)
      }

      for (n <- 1 to 10)
        if (n % 2 == 0) r1 ! Increment
        else r1 ! Decrement
      for (_ <- 1 to 10)
        r2 ! Increment

      eventually {
        r1 ! Get(r1Probe.ref)
        r1Probe.expectMessage(12L)
        r2 ! Get(r2Probe.ref)
        r2Probe.expectMessage(12L)
      }
    }
  }

  "recover from snapshot" in {
    val id = nextEntityId

    {
      val r1 = spawn(apply(id, R1, 2))
      val r2 = spawn(apply(id, R2, 2))
      val r1Probe = createTestProbe[Long]()
      val r2Probe = createTestProbe[Long]()

      r1 ! Increment
      r1 ! Increment

      eventually {
        r1 ! Get(r1Probe.ref)
        r1Probe.expectMessage(2L)
        r2 ! Get(r2Probe.ref)
        r2Probe.expectMessage(2L)
      }
    }
    {
      val r2EventProbe = createTestProbe[Counter.Updated]()
      val r2 = spawn(apply(id, R2, 2, Some(r2EventProbe.ref)))
      val r2Probe = createTestProbe[Long]()
      eventually {
        r2 ! Get(r2Probe.ref)
        r2Probe.expectMessage(2L)
      }

      r2EventProbe.expectNoMessage()
    }
  }
}
