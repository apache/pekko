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

package org.apache.pekko.persistence.testkit.query

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.LogCapturing
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.persistence.query.EventEnvelope
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin
import org.apache.pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.Effect
import org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehavior
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

object EventsByTagSpec {
  val config = PersistenceTestKitPlugin.config.withFallback(
    ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.persistence.testkit.events.serialize = off
      """))

  case class Command(evt: Seq[String], ack: ActorRef[Done])
  object Command {
    def apply(evt: String, ack: ActorRef[Done]): Command = Command(
      Seq(evt), ack
    )
  }
  case class State()

  def testBehaviour(persistenceId: String, maybeTag: Option[String]) = {
    val tag = maybeTag.getOrElse(s"$persistenceId-tag")
    (
      EventSourcedBehavior[Command, String, State](
        PersistenceId.ofUniqueId(persistenceId),
        State(),
        (_, command) =>
          Effect.persist(command.evt).thenRun { _ =>
            command.ack ! Done
          },
        (state, _) => state
      ).withTagger(_ => Set(tag)),
      tag
    )
  }

}

class EventsByTagSpec
    extends ScalaTestWithActorTestKit(EventsByPersistenceIdSpec.config)
    with LogCapturing
    with AnyWordSpecLike {
  import EventsByTagSpec._

  implicit val classic: pekko.actor.ActorSystem = system.classicSystem

  val queries =
    PersistenceQuery(system).readJournalFor[PersistenceTestKitReadJournal](PersistenceTestKitReadJournal.Identifier)

  def setup(persistenceId: String, maybeTag: Option[String] = None): (ActorRef[Command], String) = {
    val probe = createTestProbe[Done]()
    val (ref, tag) = setupEmpty(persistenceId, maybeTag)
    ref ! Command(s"$persistenceId-1", probe.ref)
    ref ! Command(s"$persistenceId-2", probe.ref)
    ref ! Command(s"$persistenceId-3", probe.ref)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    (ref, tag)
  }

  def setupBatched(persistenceId: String, maybeTag: Option[String] = None): (ActorRef[Command], String) = {
    val probe = createTestProbe[Done]()
    val (ref, tag) = setupEmpty(persistenceId, maybeTag)
    ref ! Command(Seq(s"$persistenceId-1", s"$persistenceId-2", s"$persistenceId-3"), probe.ref)
    probe.expectMessage(Done)
    (ref, tag)
  }

  def setupEmpty(persistenceId: String, maybeTag: Option[String] = None): (ActorRef[Command], String) = {
    val (ref, tag) = testBehaviour(persistenceId, maybeTag)
    (spawn(ref), tag)
  }

  "Persistent test kit live query EventsByTag" must {
    "find new events" in {
      val ackProbe = createTestProbe[Done]()
      val (ref, tag) = setup("c")
      val src = queries.eventsByTag(tag)
      val probe = src.map(_.event).runWith(TestSink.probe[Any]).request(5).expectNext("c-1", "c-2", "c-3")

      ref ! Command("c-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNext("c-4")
    }

    "find new events after batched setup" in {
      val ackProbe = createTestProbe[Done]()
      val (ref, tag) = setupBatched("d")
      val src = queries.eventsByTag(tag)
      val probe = src.map(_.event).runWith(TestSink.probe[Any]).request(5).expectNext("d-1", "d-2", "d-3")

      ref ! Command("d-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNext("d-4")
    }

    "find new events after demand request" in {
      val ackProbe = createTestProbe[Done]()
      val (ref, tag) = setup("e")
      val src = queries.eventsByTag(tag)
      val probe =
        src.map(_.event).runWith(TestSink.probe[Any]).request(2).expectNext("e-1", "e-2").expectNoMessage(100.millis)

      ref ! Command("e-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNoMessage(100.millis).request(5).expectNext("e-3").expectNext("e-4")
    }

    "include timestamp in EventEnvelope" in {
      val (_, tag) = setup("n")

      val src = queries.eventsByTag(tag)
      val probe = src.runWith(TestSink.probe[EventEnvelope])

      probe.request(5)
      probe.expectNext().timestamp should be > 0L
      probe.expectNext().timestamp should be > 0L
      probe.cancel()
    }

    "not complete for empty tag" in {
      val tag = "o-tag"
      val ackProbe = createTestProbe[Done]()
      val src = queries.eventsByTag(tag)
      val probe =
        src.map(_.event).runWith(TestSink.probe[Any]).request(2)

      probe.expectNoMessage(200.millis) // must not complete

      val (ref, _) = setupEmpty("o", Some(tag))
      ref ! Command("o-1", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.cancel()
    }
  }
}
