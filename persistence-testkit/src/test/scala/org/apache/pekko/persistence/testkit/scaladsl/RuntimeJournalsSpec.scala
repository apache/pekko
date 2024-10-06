package org.apache.pekko.persistence.testkit.scaladsl

import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.LogCapturing
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.persistence.JournalProtocol.RecoverySuccess
import org.apache.pekko.persistence.JournalProtocol.ReplayMessages
import org.apache.pekko.persistence.JournalProtocol.ReplayedMessage
import org.apache.pekko.persistence.Persistence
import org.apache.pekko.persistence.SelectedSnapshot
import org.apache.pekko.persistence.SnapshotProtocol.LoadSnapshot
import org.apache.pekko.persistence.SnapshotProtocol.LoadSnapshotResult
import org.apache.pekko.persistence.SnapshotSelectionCriteria
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin
import org.apache.pekko.persistence.testkit.PersistenceTestKitSnapshotPlugin
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.Effect
import org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehavior
import org.apache.pekko.persistence.typed.scaladsl.RetentionCriteria
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

  "The testkit journal and query plugin" must {

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
