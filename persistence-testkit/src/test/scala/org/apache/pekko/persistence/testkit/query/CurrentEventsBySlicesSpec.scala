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

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.persistence.Persistence
import pekko.persistence.query.NoOffset
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.testkit.query.EventsByPersistenceIdSpec.Command
import pekko.persistence.testkit.query.EventsByPersistenceIdSpec.testBehaviour
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.stream.scaladsl.Sink

class CurrentEventsBySlicesSpec
    extends ScalaTestWithActorTestKit(EventsByPersistenceIdSpec.config)
    with LogCapturing
    with AnyWordSpecLike {

  implicit val classic: pekko.actor.ActorSystem = system.classicSystem

  val queries =
    PersistenceQuery(system).readJournalFor[PersistenceTestKitReadJournal](PersistenceTestKitReadJournal.Identifier)

  def setup(persistenceId: String): ActorRef[Command] = {
    val probe = createTestProbe[Done]()
    val ref = spawn(testBehaviour(persistenceId))
    ref ! Command(s"$persistenceId-1", probe.ref)
    ref ! Command(s"$persistenceId-2", probe.ref)
    ref ! Command(s"$persistenceId-3", probe.ref)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    ref
  }

  "Persistent test kit currentEventsByTag query" must {

    "find eventsBySlices ordered by insert time" in {
      val probe = createTestProbe[Done]()
      val ref1 = spawn(testBehaviour("Test|pid-1"))
      val ref2 = spawn(testBehaviour("Test|pid-2"))
      ref1 ! Command("evt-1", probe.ref)
      ref1 ! Command("evt-2", probe.ref)
      ref1 ! Command("evt-3", probe.ref)
      probe.receiveMessages(3)
      ref2 ! Command("evt-4", probe.ref)
      probe.receiveMessage()
      ref1 ! Command("evt-5", probe.ref)
      probe.receiveMessage()

      queries
        .currentEventsBySlices[String]("Test", 0, Persistence(system).numberOfSlices - 1, NoOffset)
        .runWith(Sink.seq)
        .futureValue
        .map(_.event) should ===(Seq("evt-1", "evt-2", "evt-3", "evt-4", "evt-5"))
    }
  }

}
