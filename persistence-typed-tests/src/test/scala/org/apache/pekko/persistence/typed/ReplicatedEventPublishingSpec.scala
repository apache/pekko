/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.internal.{ ReplicatedPublishedEventMetaData, VersionVector }
import pekko.persistence.typed.scaladsl.ReplicatedEventSourcing
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import org.scalatest.wordspec.AnyWordSpecLike

object ReplicatedEventPublishingSpec {

  val EntityType = "EventPublishingSpec"

  object MyReplicatedBehavior {
    trait Command
    case class Add(text: String, replyTo: ActorRef[Done]) extends Command
    case class Get(replyTo: ActorRef[Set[String]]) extends Command
    case object Stop extends Command

    def apply(entityId: String, replicaId: ReplicaId, allReplicas: Set[ReplicaId]): Behavior[Command] =
      Behaviors.setup { ctx =>
        ReplicatedEventSourcing.commonJournalConfig(
          ReplicationId(EntityType, entityId, replicaId),
          allReplicas,
          PersistenceTestKitReadJournal.Identifier)(replicationContext =>
          EventSourcedBehavior[Command, String, Set[String]](
            replicationContext.persistenceId,
            Set.empty,
            (state, command) =>
              command match {
                case Add(string, replyTo) =>
                  ctx.log.debug("Persisting [{}]", string)
                  Effect.persist(string).thenRun { _ =>
                    ctx.log.debug("Ack:ing [{}]", string)
                    replyTo ! Done
                  }
                case Get(replyTo) =>
                  replyTo ! state
                  Effect.none
                case Stop =>
                  Effect.stop()
                case unexpected => throw new RuntimeException(s"Unexpected: $unexpected")
              },
            (state, string) => state + string))
      }
  }
}

class ReplicatedEventPublishingSpec
    extends ScalaTestWithActorTestKit(PersistenceTestKitPlugin.config)
    with AnyWordSpecLike
    with LogCapturing {

  val DCA = ReplicaId("DC-A")
  val DCB = ReplicaId("DC-B")
  val DCC = ReplicaId("DC-C")

  private var idCounter = 0
  def nextEntityId(): String = {
    idCounter += 1
    s"myId$idCounter"
  }

  import ReplicatedEventPublishingSpec._

  "An Replicated Event Sourced actor" must {
    "move forward when a published event from a replica is received" in {
      val id = nextEntityId()
      val actor = spawn(MyReplicatedBehavior(id, DCA, Set(DCA, DCB)))
      val probe = createTestProbe[Any]()
      actor ! MyReplicatedBehavior.Add("one", probe.ref)
      probe.expectMessage(Done)

      // simulate a published event from another replica
      actor.asInstanceOf[ActorRef[Any]] ! internal.PublishedEventImpl(
        ReplicationId(EntityType, id, DCB).persistenceId,
        1L,
        "two",
        System.currentTimeMillis(),
        Some(new ReplicatedPublishedEventMetaData(DCB, VersionVector.empty)))
      actor ! MyReplicatedBehavior.Add("three", probe.ref)
      probe.expectMessage(Done)

      actor ! MyReplicatedBehavior.Get(probe.ref)
      probe.expectMessage(Set("one", "two", "three"))
    }

    "ignore a published event from a replica is received but the sequence number is unexpected" in {
      val id = nextEntityId()
      val actor = spawn(MyReplicatedBehavior(id, DCA, Set(DCA, DCB)))
      val probe = createTestProbe[Any]()
      actor ! MyReplicatedBehavior.Add("one", probe.ref)
      probe.expectMessage(Done)

      // simulate a published event from another replica
      actor.asInstanceOf[ActorRef[Any]] ! internal.PublishedEventImpl(
        ReplicationId(EntityType, id, DCB).persistenceId,
        2L, // missing 1L
        "two",
        System.currentTimeMillis(),
        Some(new ReplicatedPublishedEventMetaData(DCB, VersionVector.empty)))
      actor ! MyReplicatedBehavior.Add("three", probe.ref)
      probe.expectMessage(Done)

      actor ! MyReplicatedBehavior.Get(probe.ref)
      probe.expectMessage(Set("one", "three"))
    }

    "ignore a published event from an unknown replica" in {
      val id = nextEntityId()
      val actor = spawn(MyReplicatedBehavior(id, DCA, Set(DCA, DCB)))
      val probe = createTestProbe[Any]()
      actor ! MyReplicatedBehavior.Add("one", probe.ref)
      probe.expectMessage(Done)

      // simulate a published event from another replica
      actor.asInstanceOf[ActorRef[Any]] ! internal.PublishedEventImpl(
        ReplicationId(EntityType, id, DCC).persistenceId,
        1L,
        "two",
        System.currentTimeMillis(),
        Some(new ReplicatedPublishedEventMetaData(DCC, VersionVector.empty)))
      actor ! MyReplicatedBehavior.Add("three", probe.ref)
      probe.expectMessage(Done)

      actor ! MyReplicatedBehavior.Get(probe.ref)
      probe.expectMessage(Set("one", "three"))
    }

    "ignore an already seen event from a replica" in {
      val id = nextEntityId()
      val actor = spawn(MyReplicatedBehavior(id, DCA, Set(DCA, DCB)))
      val probe = createTestProbe[Any]()
      actor ! MyReplicatedBehavior.Add("one", probe.ref)
      probe.expectMessage(Done)

      // simulate a published event from another replica
      actor.asInstanceOf[ActorRef[Any]] ! internal.PublishedEventImpl(
        ReplicationId(EntityType, "myId4", DCB).persistenceId,
        1L,
        "two",
        System.currentTimeMillis(),
        Some(new ReplicatedPublishedEventMetaData(DCB, VersionVector.empty)))
      // simulate another published event from that replica
      actor.asInstanceOf[ActorRef[Any]] ! internal.PublishedEventImpl(
        ReplicationId(EntityType, id, DCB).persistenceId,
        1L,
        "two-again", // ofc this would be the same in the real world, different just so we can detect
        System.currentTimeMillis(),
        Some(new ReplicatedPublishedEventMetaData(DCB, VersionVector.empty)))

      actor ! MyReplicatedBehavior.Add("three", probe.ref)
      probe.expectMessage(Done)

      actor ! MyReplicatedBehavior.Get(probe.ref)
      probe.expectMessage(Set("one", "two", "three"))
    }

    "handle published events after replay" in {
      val id = nextEntityId()
      val probe = createTestProbe[Any]()
      val replicatedBehavior = MyReplicatedBehavior(id, DCA, Set(DCA, DCB))
      val incarnation1 = spawn(replicatedBehavior)
      incarnation1 ! MyReplicatedBehavior.Add("one", probe.ref)
      probe.expectMessage(Done)

      incarnation1 ! MyReplicatedBehavior.Stop
      probe.expectTerminated(incarnation1)

      val incarnation2 = spawn(replicatedBehavior)

      incarnation2 ! MyReplicatedBehavior.Get(probe.ref)
      probe.expectMessage(Set("one"))
      // replay completed

      // simulate a published event from another replica
      incarnation2.asInstanceOf[ActorRef[Any]] ! internal.PublishedEventImpl(
        ReplicationId(EntityType, id, DCB).persistenceId,
        1L,
        "two",
        System.currentTimeMillis(),
        Some(new ReplicatedPublishedEventMetaData(DCB, VersionVector.empty)))

      incarnation2 ! MyReplicatedBehavior.Add("three", probe.ref)
      probe.expectMessage(Done)

      incarnation2 ! MyReplicatedBehavior.Get(probe.ref)
      probe.expectMessage(Set("one", "two", "three"))
    }

    "handle published events before and after replay" in {
      val id = nextEntityId()
      val probe = createTestProbe[Any]()
      val replicatedBehaviorA = MyReplicatedBehavior(id, DCA, Set(DCA, DCB))
      val incarnationA1 = spawn(replicatedBehaviorA)
      incarnationA1 ! MyReplicatedBehavior.Add("one", probe.ref)
      probe.expectMessage(Done)

      // simulate a published event from another replica
      incarnationA1.asInstanceOf[ActorRef[Any]] ! internal.PublishedEventImpl(
        ReplicationId(EntityType, id, DCB).persistenceId,
        1L,
        "two",
        System.currentTimeMillis(),
        Some(new ReplicatedPublishedEventMetaData(DCB, VersionVector.empty)))

      incarnationA1 ! MyReplicatedBehavior.Stop
      probe.expectTerminated(incarnationA1)

      val incarnationA2 = spawn(replicatedBehaviorA)

      // simulate a published event from another replica
      incarnationA2.asInstanceOf[ActorRef[Any]] ! internal.PublishedEventImpl(
        ReplicationId(EntityType, id, DCB).persistenceId,
        2L,
        "three",
        System.currentTimeMillis(),
        Some(new ReplicatedPublishedEventMetaData(DCB, VersionVector.empty)))

      incarnationA2 ! MyReplicatedBehavior.Add("four", probe.ref)
      probe.expectMessage(Done)

      incarnationA2 ! MyReplicatedBehavior.Get(probe.ref)
      probe.expectMessage(Set("one", "two", "three", "four"))
    }

  }

}
