/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence.testkit.query

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.persistence.query.EventEnvelope
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import org.scalatest.wordspec.AnyWordSpecLike

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

  def testBehaviour(persistenceId: String, tags: Set[String]) = {
    EventSourcedBehavior[Command, String, State](
      PersistenceId.ofUniqueId(persistenceId),
      State(),
      (_, command) =>
        Effect.persist(command.evt).thenRun { _ =>
          command.ack ! Done
        },
      (state, _) => state
    ).withTagger(_ => tags)
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

  def setup(persistenceId: String, tags: Set[String]): ActorRef[Command] = {
    val probe = createTestProbe[Done]()
    val ref = setupEmpty(persistenceId, tags)
    ref ! Command(s"$persistenceId-1", probe.ref)
    ref ! Command(s"$persistenceId-2", probe.ref)
    ref ! Command(s"$persistenceId-3", probe.ref)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    ref
  }

  def setupBatched(persistenceId: String, tags: Set[String]): ActorRef[Command] = {
    val probe = createTestProbe[Done]()
    val ref = setupEmpty(persistenceId, tags)
    ref ! Command(Seq(s"$persistenceId-1", s"$persistenceId-2", s"$persistenceId-3"), probe.ref)
    probe.expectMessage(Done)
    ref
  }

  def setupEmpty(persistenceId: String, tags: Set[String]): ActorRef[Command] = {
    spawn(testBehaviour(persistenceId, tags))
  }

  "Persistent test kit live query EventsByTag" must {
    "find new events" in {
      val ackProbe = createTestProbe[Done]()
      val tag = "c-tag"
      val ref = setup("c", Set(tag))
      val src = queries.eventsByTag(tag)
      val probe = src.map(_.event).runWith(TestSink.probe[Any]).request(5).expectNext("c-1", "c-2", "c-3")

      ref ! Command("c-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNext("c-4")
    }

    "find new events after batched setup" in {
      val ackProbe = createTestProbe[Done]()
      val tag = "d-tag"
      val ref = setupBatched("d", Set(tag))
      val src = queries.eventsByTag(tag)
      val probe = src.map(_.event).runWith(TestSink.probe[Any]).request(5).expectNext("d-1", "d-2", "d-3")

      ref ! Command("d-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNext("d-4")
    }

    "find new events after demand request" in {
      val ackProbe = createTestProbe[Done]()
      val tag = "e-tag"
      val ref = setup("e", Set("e-tag"))
      val src = queries.eventsByTag(tag)
      val probe =
        src.map(_.event).runWith(TestSink.probe[Any]).request(2).expectNext("e-1", "e-2").expectNoMessage(100.millis)

      ref ! Command("e-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNoMessage(100.millis).request(5).expectNext("e-3").expectNext("e-4")
    }

    "include timestamp in EventEnvelope" in {
      val tag = "n-tag"
      setup("n", Set(tag))

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

      val ref = setupEmpty("o", Set(tag))
      ref ! Command("o-1", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.cancel()
    }

    "find new events in order that they were persisted when the tag is used by multiple persistence IDs" in {
      val tag = "f-tag"
      val ackProbe = createTestProbe[Done]()
      val src = queries.eventsByTag(tag)
      val probe = src
        .map { ee =>
          (ee.persistenceId, ee.event)
        }
        .runWith(TestSink.probe[(String, Any)])

      val ref2 = setupEmpty("f2", Set(tag))
      ref2 ! Command(Seq("f2-1", "f2-2"), ackProbe.ref)
      ackProbe.expectMessage(Done)
      probe.request(2)
      probe.expectNext() shouldBe ("f2", "f2-1")
      probe.expectNext() shouldBe ("f2", "f2-2")

      val ref1 = setupEmpty("f1", Set(tag))
      ref1 ! Command(Seq("f1-1", "f1-2"), ackProbe.ref)
      ackProbe.expectMessage(Done)
      probe.request(2)
      probe.expectNext() shouldBe ("f1", "f1-1")
      probe.expectNext() shouldBe ("f1", "f1-2")
    }

    "find new events when persistence ID uses multiple tags" in {
      val tag1 = "g-tag"
      val tag2 = "h-tag"
      val ackProbe = createTestProbe[Done]()
      def setupProbe(tag: String) = {
        queries.eventsByTag(tag)
          .map(_.event)
          .runWith(TestSink.probe[Any])
      }
      val probe1 = setupProbe(tag1)
      probe1.request(1).expectNoMessage(100.millis)
      val probe2 = setupProbe(tag2)
      probe2.request(1).expectNoMessage(100.millis)

      val ref2 = setupEmpty("gh", Set(tag1, tag2))
      ref2 ! Command(Seq("gh-1", "gh-2"), ackProbe.ref)
      ackProbe.expectMessage(Done)

      def assertProbe(probe: TestSubscriber.Probe[Any]) = {
        probe.request(2).expectNextN(Seq("gh-1", "gh-2"))
      }
      assertProbe(probe1)
      assertProbe(probe2)
    }
  }
}
