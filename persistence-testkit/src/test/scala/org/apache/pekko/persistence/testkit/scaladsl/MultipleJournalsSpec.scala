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

package org.apache.pekko.persistence.testkit.scaladsl

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.scaladsl.CurrentEventsByPersistenceIdQuery
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.stream.scaladsl.Sink

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object MultipleJournalsSpec {

  object ListActor {
    sealed trait Command
    case class Save(text: String, replyTo: ActorRef[Done]) extends Command
    case class ShowMeWhatYouGot(replyTo: ActorRef[Set[String]]) extends Command
    case object Stop extends Command

    def apply(persistenceId: String, journal: String): Behavior[Command] =
      EventSourcedBehavior[Command, String, Set[String]](
        PersistenceId.ofUniqueId(persistenceId),
        Set.empty[String],
        (state, cmd) =>
          cmd match {
            case Save(text, replyTo) =>
              Effect.persist(text).thenRun(_ => replyTo ! Done)
            case ShowMeWhatYouGot(replyTo) =>
              replyTo ! state
              Effect.none
            case Stop =>
              Effect.stop()
          },
        (state, evt) => state + evt).withJournalPluginId(journal)

  }

  def config = ConfigFactory.parseString(s"""
    journal1 {
      # journal and query expected to be next to each other under config path
      journal.class = "${classOf[PersistenceTestKitPlugin].getName}"
      query = $${pekko.persistence.testkit.query}
    }
    journal2 {
      journal.class = "${classOf[PersistenceTestKitPlugin].getName}"
      query = $${pekko.persistence.testkit.query}
    }
  """).withFallback(ConfigFactory.load()).resolve()

}

class MultipleJournalsSpec
    extends ScalaTestWithActorTestKit(MultipleJournalsSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  import MultipleJournalsSpec._

  "The testkit journal and query plugin" must {

    "be possible to configure and use in multiple isolated instances" in {
      val probe = createTestProbe[Any]()

      {
        // one actor in each journal with same id
        val j1 = spawn(ListActor("id1", "journal1.journal"))
        val j2 = spawn(ListActor("id1", "journal2.journal"))
        j1 ! ListActor.Save("j1m1", probe.ref)
        probe.receiveMessage()
        j2 ! ListActor.Save("j2m1", probe.ref)
        probe.receiveMessage()

        j1 ! ListActor.Stop
        probe.expectTerminated(j1)
        j2 ! ListActor.Stop
        probe.expectTerminated(j2)
      }

      {
        // new incarnations in each journal with same id
        val j1 = spawn(ListActor("id1", "journal1.journal"))
        val j2 = spawn(ListActor("id1", "journal2.journal"))

        // does not see each others events
        j1 ! ListActor.ShowMeWhatYouGot(probe.ref)
        probe.expectMessage(Set("j1m1"))
        j2 ! ListActor.ShowMeWhatYouGot(probe.ref)
        probe.expectMessage(Set("j2m1"))
      }

      val readJournal1 = PersistenceQuery(system).readJournalFor[CurrentEventsByPersistenceIdQuery]("journal1.query")
      val readJournal2 = PersistenceQuery(system).readJournalFor[CurrentEventsByPersistenceIdQuery]("journal2.query")

      val eventsForJournal1 =
        readJournal1.currentEventsByPersistenceId("id1", 0L, Long.MaxValue).runWith(Sink.seq).futureValue
      eventsForJournal1.map(_.event) should ===(Seq("j1m1"))

      val eventsForJournal2 =
        readJournal2.currentEventsByPersistenceId("id1", 0L, Long.MaxValue).runWith(Sink.seq).futureValue
      eventsForJournal2.map(_.event) should ===(Seq("j2m1"))
    }

  }

}
