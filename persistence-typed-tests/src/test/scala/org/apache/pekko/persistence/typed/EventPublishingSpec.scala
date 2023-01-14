/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

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
import pekko.actor.typed.eventstream.EventStream
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.serialization.jackson.CborSerializable
import org.scalatest.wordspec.AnyWordSpecLike

object EventPublishingSpec {

  object WowSuchEventSourcingBehavior {
    sealed trait Command
    case class StoreThis(data: String, tagIt: Boolean, replyTo: ActorRef[Done]) extends Command

    final case class Event(data: String, tagIt: Boolean) extends CborSerializable

    def apply(id: PersistenceId): Behavior[Command] =
      EventSourcedBehavior[Command, Event, Set[Event]](
        id,
        Set.empty,
        (_, command) =>
          command match {
            case StoreThis(data, tagIt, replyTo) =>
              Effect.persist(Event(data, tagIt)).thenRun(_ => replyTo ! Done)
          },
        (state, event) => state + event)
        .withTagger(evt => if (evt.tagIt) Set("tag") else Set.empty)
        .withEventPublishing(enabled = true)
  }
}

class EventPublishingSpec
    extends ScalaTestWithActorTestKit(PersistenceTestKitPlugin.config)
    with AnyWordSpecLike
    with LogCapturing {

  import EventPublishingSpec._

  "EventPublishing support" must {

    "publish events after written for any actor" in {
      val topicProbe = createTestProbe[PublishedEvent]()
      system.eventStream ! EventStream.Subscribe(topicProbe.ref)
      // We don't verify subscription completed (no ack available), but expect the next steps to take enough time
      // for subscription to complete

      val myId = PersistenceId.ofUniqueId("myId")
      val wowSuchActor = spawn(WowSuchEventSourcingBehavior(myId))

      val persistProbe = createTestProbe[Any]()
      wowSuchActor ! WowSuchEventSourcingBehavior.StoreThis("great stuff", tagIt = false, replyTo = persistProbe.ref)
      persistProbe.expectMessage(Done)

      val published1 = topicProbe.receiveMessage()
      published1.persistenceId should ===(myId)
      published1.event should ===(WowSuchEventSourcingBehavior.Event("great stuff", false))
      published1.sequenceNumber should ===(1L)
      published1.tags should ===(Set.empty)

      val anotherId = PersistenceId.ofUniqueId("anotherId")
      val anotherActor = spawn(WowSuchEventSourcingBehavior(anotherId))
      anotherActor ! WowSuchEventSourcingBehavior.StoreThis("another event", tagIt = true, replyTo = persistProbe.ref)
      persistProbe.expectMessage(Done)

      val published2 = topicProbe.receiveMessage()
      published2.persistenceId should ===(anotherId)
      published2.event should ===(WowSuchEventSourcingBehavior.Event("another event", true))
      published2.sequenceNumber should ===(1L)
      published2.tags should ===(Set("tag"))
    }

  }

}
