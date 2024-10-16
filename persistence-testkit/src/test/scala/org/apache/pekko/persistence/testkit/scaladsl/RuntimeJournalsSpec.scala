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

package org.apache.pekko.persistence.testkit.scaladsl

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.adapter._
import pekko.persistence.JournalProtocol.RecoverySuccess
import pekko.persistence.JournalProtocol.ReplayMessages
import pekko.persistence.JournalProtocol.ReplayedMessage
import pekko.persistence.Persistence
import pekko.persistence.SelectedSnapshot
import pekko.persistence.SnapshotProtocol.LoadSnapshot
import pekko.persistence.SnapshotProtocol.LoadSnapshotResult
import pekko.persistence.SnapshotSelectionCriteria
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.PersistenceTestKitSnapshotPlugin
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.persistence.typed.scaladsl.RetentionCriteria
import org.scalatest.Inside
import org.scalatest.wordspec.AnyWordSpecLike

object RuntimeJournalsSpec {

  private object Actor {
    sealed trait Command
    case class Save(text: String, replyTo: ActorRef[Done]) extends Command
    case class ShowMeWhatYouGot(replyTo: ActorRef[String]) extends Command
    case object Stop extends Command

    def apply(persistenceId: String, journal: String): Behavior[Command] =
      EventSourcedBehavior[Command, String, String](
        PersistenceId.ofUniqueId(persistenceId),
        "",
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
        (state, evt) => Seq(state, evt).filter(_.nonEmpty).mkString("|"))
        .withRetention(RetentionCriteria.snapshotEvery(1, Int.MaxValue))
        .withJournalPluginId(s"$journal.journal")
        .withJournalPluginConfig(Some(config(journal)))
        .withSnapshotPluginId(s"$journal.snapshot")
        .withSnapshotPluginConfig(Some(config(journal)))

  }

  private def config(journal: String) = {
    ConfigFactory.parseString(s"""
      $journal {
        journal.class = "${classOf[PersistenceTestKitPlugin].getName}"
        snapshot.class = "${classOf[PersistenceTestKitSnapshotPlugin].getName}"
      }
    """)
  }
}

class RuntimeJournalsSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with LogCapturing
    with Inside {

  import RuntimeJournalsSpec._

  "The testkit journal and snapshot store plugins" must {

    "be possible to configure at runtime and use in multiple isolated instances" in {
      val probe = createTestProbe[Any]()

      {
        // one actor in each journal with same id
        val j1 = spawn(Actor("id1", "journal1"))
        val j2 = spawn(Actor("id1", "journal2"))
        j1 ! Actor.Save("j1m1", probe.ref)
        probe.receiveMessage()
        j2 ! Actor.Save("j2m1", probe.ref)
        probe.receiveMessage()
      }

      {
        def assertJournal(journal: String, expectedEvent: String) = {
          val ref = Persistence(system).journalFor(s"$journal.journal", config(journal))
          ref.tell(ReplayMessages(0, Long.MaxValue, Long.MaxValue, "id1", probe.ref.toClassic), probe.ref.toClassic)
          inside(probe.receiveMessage()) {
            case ReplayedMessage(persistentRepr) =>
              persistentRepr.persistenceId shouldBe "id1"
              persistentRepr.payload shouldBe expectedEvent
          }
          probe.expectMessage(RecoverySuccess(1))
        }

        assertJournal("journal1", "j1m1")
        assertJournal("journal2", "j2m1")
      }

      {
        def assertSnapshot(journal: String, expectedShapshot: String) = {
          val ref = Persistence(system).snapshotStoreFor(s"$journal.snapshot", config(journal))
          ref.tell(LoadSnapshot("id1", SnapshotSelectionCriteria.Latest, Long.MaxValue),
            probe.ref.toClassic)
          inside(probe.receiveMessage()) {
            case LoadSnapshotResult(Some(SelectedSnapshot(_, snapshot)), _) =>
              snapshot shouldBe expectedShapshot
          }
        }

        assertSnapshot("journal1", "j1m1")
        assertSnapshot("journal2", "j2m1")
      }
    }
  }
}
