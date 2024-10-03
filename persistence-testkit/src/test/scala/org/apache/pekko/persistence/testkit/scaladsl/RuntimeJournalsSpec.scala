package org.apache.pekko.persistence.testkit.scaladsl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.LogCapturing
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin
import org.apache.pekko.persistence.testkit.PersistenceTestKitSnapshotPlugin
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.Effect
import org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehavior
import org.apache.pekko.persistence.typed.scaladsl.RetentionCriteria
import org.scalatest.wordspec.AnyWordSpecLike

object RuntimeJournalsSpec {

  object ListActor {
    sealed trait Command
    case class Save(text: String, replyTo: ActorRef[Done]) extends Command
    case class ShowMeWhatYouGot(replyTo: ActorRef[Set[String]]) extends Command
    case object Stop extends Command

    def apply(persistenceId: String, journal: String, config: Config): Behavior[Command] =
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
        (state, evt) => state + evt)
        .withRetention(RetentionCriteria.snapshotEvery(1, Int.MaxValue))
        .withJournalPluginId(s"$journal.journal")
        .withJournalPluginConfig(Some(config))
        .withSnapshotPluginId(s"$journal.snapshot")
        .withSnapshotPluginConfig(Some(config))

  }

  def config1 = ConfigFactory.parseString(s"""
    journal1 {
      journal.class = "${classOf[PersistenceTestKitPlugin].getName}"
      snapshot.class = "${classOf[PersistenceTestKitSnapshotPlugin].getName}"
    }
  """)

  def config2 = ConfigFactory.parseString(s"""
    journal2 {
      journal.class = "${classOf[PersistenceTestKitPlugin].getName}"
      snapshot.class = "${classOf[PersistenceTestKitSnapshotPlugin].getName}"
    }
  """).resolve()

}

class RuntimeJournalsSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with LogCapturing {

  import RuntimeJournalsSpec._

  "The testkit journal and query plugin" must {

    "be possible to configure at runtime and use in multiple isolated instances" in {
      val probe = createTestProbe[Any]()

      {
        // one actor in each journal with same id
        val j1 = spawn(ListActor("id1", "journal1", config1))
        val j2 = spawn(ListActor("id1", "journal2", config2))
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
        val j1 = spawn(ListActor("id1", "journal1", config1))
        val j2 = spawn(ListActor("id1", "journal2", config2))

        // does not see each others events
        j1 ! ListActor.ShowMeWhatYouGot(probe.ref)
        probe.expectMessage(Set("j1m1"))
        j2 ! ListActor.ShowMeWhatYouGot(probe.ref)
        probe.expectMessage(Set("j2m1"))
      }

      // TODO test snapshot journal
    }

  }

}
