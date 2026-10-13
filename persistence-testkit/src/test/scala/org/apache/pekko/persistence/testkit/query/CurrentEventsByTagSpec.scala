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

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.persistence.query.NoOffset
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.Sequence
import pekko.persistence.testkit.query.EventsByPersistenceIdSpec.Command
import pekko.persistence.testkit.query.EventsByPersistenceIdSpec.testBehavior
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.stream.scaladsl.Sink

import org.scalatest.wordspec.AnyWordSpecLike

class CurrentEventsByTagSpec
    extends ScalaTestWithActorTestKit(EventsByPersistenceIdSpec.config)
    with LogCapturing
    with AnyWordSpecLike {

  implicit val classic: pekko.actor.ActorSystem = system.classicSystem

  private val queries =
    PersistenceQuery(system).readJournalFor[PersistenceTestKitReadJournal](PersistenceTestKitReadJournal.Identifier)

  def setupEmpty(persistenceId: String): ActorRef[Command] = {
    spawn(
      testBehavior(persistenceId).withTagger(evt =>
        if (evt.indexOf('-') > 0) Set(evt.split('-')(1), "all")
        else Set("all")))
  }

  "Persistent test kit currentEventsByTag query" must {

    "find tagged events ordered by insert time" in {
      val probe = createTestProbe[Done]()
      val ref1 = setupEmpty("taggedpid-1")
      val ref2 = setupEmpty("taggedpid-2")
      ref1 ! Command("evt-1", probe.ref)
      ref1 ! Command("evt-2", probe.ref)
      ref1 ! Command("evt-3", probe.ref)
      probe.receiveMessages(3)
      ref2 ! Command("evt-4", probe.ref)
      probe.receiveMessage()
      ref1 ! Command("evt-5", probe.ref)
      probe.receiveMessage()

      queries.currentEventsByTag("all", NoOffset).runWith(Sink.seq).futureValue.map(_.event) should ===(
        Seq("evt-1", "evt-2", "evt-3", "evt-4", "evt-5"))
    }

    "find tagged events after the given offset" in {
      val probe = createTestProbe[Done]()
      val ref1 = setupEmpty("offsetpid-1")
      val ref2 = setupEmpty("offsetpid-2")
      ref1 ! Command("evt1-offset", probe.ref)
      ref1 ! Command("evt2-offset", probe.ref)
      probe.receiveMessages(2)
      ref2 ! Command("evt3-offset", probe.ref)
      probe.receiveMessage()
      ref1 ! Command("evt4-offset", probe.ref)
      probe.receiveMessage()

      val all = queries.currentEventsByTag("offset", NoOffset).runWith(Sink.seq).futureValue
      all.map(_.event) should ===(Seq("evt1-offset", "evt2-offset", "evt3-offset", "evt4-offset"))

      queries.currentEventsByTag("offset", all(1).offset).runWith(Sink.seq).futureValue.map(_.event) should ===(
        Seq("evt3-offset", "evt4-offset"))
      queries.currentEventsByTag("offset", all.last.offset).runWith(Sink.seq).futureValue shouldBe empty
    }

    "reject unsupported offset types" in {
      intercept[IllegalArgumentException] {
        queries.currentEventsByTag("all", Sequence(1L))
      }
    }
  }

}
