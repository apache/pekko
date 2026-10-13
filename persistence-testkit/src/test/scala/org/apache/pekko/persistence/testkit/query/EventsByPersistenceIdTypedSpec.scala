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

package org.apache.pekko.persistence.testkit.query

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.persistence.Persistence
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.TimestampOffset
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.stream.scaladsl.Sink
import pekko.stream.testkit.scaladsl.TestSink

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object EventsByPersistenceIdTypedSpec {
  val config = PersistenceTestKitPlugin.config.withFallback(
    ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.persistence.testkit.events.serialize = off
      """))

  case class Command(evt: String, ack: ActorRef[Done])
  case class State()

  def testBehavior(persistenceId: String) = {
    EventSourcedBehavior[Command, String, State](
      PersistenceId.ofUniqueId(persistenceId),
      State(),
      (_, command) =>
        Effect.persist(command.evt).thenRun { _ =>
          command.ack ! Done
        },
      (state, _) => state).withTagger(evt => if (evt.startsWith("tag-me-")) Set("tag") else Set.empty)
  }

}

class EventsByPersistenceIdTypedSpec
    extends ScalaTestWithActorTestKit(EventsByPersistenceIdTypedSpec.config)
    with LogCapturing
    with AnyWordSpecLike {
  import EventsByPersistenceIdTypedSpec._

  implicit val classic: pekko.actor.ActorSystem = system.classicSystem

  val queries =
    PersistenceQuery(system).readJournalFor[PersistenceTestKitReadJournal](PersistenceTestKitReadJournal.Identifier)

  def setup(persistenceId: String): ActorRef[Command] = {
    val probe = createTestProbe[Done]()
    val ref = setupEmpty(persistenceId)
    ref ! Command(s"$persistenceId-1", probe.ref)
    ref ! Command(s"$persistenceId-2", probe.ref)
    ref ! Command(s"$persistenceId-3", probe.ref)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    ref
  }

  def setupEmpty(persistenceId: String): ActorRef[Command] = {
    spawn(testBehavior(persistenceId))
  }

  "Persistent test kit live query eventsByPersistenceIdTyped" must {
    "find new events" in {
      val ackProbe = createTestProbe[Done]()
      val ref = setup("d")
      val src = queries.eventsByPersistenceIdTyped[String]("d", 0L, Long.MaxValue)
      val probe = src.runWith(TestSink[EventEnvelope[String]]())
      probe.request(5)
      probe.expectNextN(3)

      ref ! Command("tag-me-d-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      val envelope = probe.expectNext()
      envelope.offset shouldBe a[TimestampOffset]
      envelope.event should ===("tag-me-d-4")
      envelope.tags should ===(Set("tag"))
      envelope.filtered should ===(false)
      envelope.source should ===("")
      envelope.slice should ===(Persistence(system).sliceForPersistenceId("d"))

      val currentResult =
        queries.currentEventsByPersistenceIdTyped[String]("d", 0L, Long.MaxValue).runWith(Sink.seq).futureValue
      currentResult should have size 4
      currentResult.last should ===(envelope)
    }
  }
}
