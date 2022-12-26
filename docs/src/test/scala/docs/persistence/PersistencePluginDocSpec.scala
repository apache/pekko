/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.persistence

import scala.collection.immutable
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import com.typesafe.config._
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.duration._

//#plugin-imports
import org.apache.pekko
import pekko.persistence._
import pekko.persistence.journal._
import pekko.persistence.snapshot._

//#plugin-imports

object PersistencePluginDocSpec {
  val config =
    """
      //#leveldb-plugin-config
      # Path to the journal plugin to be used
      pekko.persistence.journal.plugin = "pekko.persistence.journal.leveldb"
      //#leveldb-plugin-config

      //#leveldb-snapshot-plugin-config
      # Path to the snapshot store plugin to be used
      pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
      //#leveldb-snapshot-plugin-config

      //#max-message-batch-size
      pekko.persistence.journal.leveldb.max-message-batch-size = 200
      //#max-message-batch-size
      //#journal-config
      pekko.persistence.journal.leveldb.dir = "target/journal"
      //#journal-config
      //#snapshot-config
      pekko.persistence.snapshot-store.local.dir = "target/snapshots"
      //#snapshot-config
      //#native-config
      pekko.persistence.journal.leveldb.native = off
      //#native-config

      //#compaction-intervals-config
      # Number of deleted messages per persistence id that will trigger journal compaction
      pekko.persistence.journal.leveldb.compaction-intervals {
        persistence-id-1 = 100
        persistence-id-2 = 200
        # ...
        persistence-id-N = 1000
        # use wildcards to match unspecified persistence ids, if any
        "*" = 250
      }
      //#compaction-intervals-config
    """
}

class PersistencePluginDocSpec extends AnyWordSpec {
  {
    val providerConfig =
      """
        //#journal-plugin-config
        # Path to the journal plugin to be used
        pekko.persistence.journal.plugin = "my-journal"

        # My custom journal plugin
        my-journal {
          # Class name of the plugin.
          class = "docs.persistence.MyJournal"
          # Dispatcher for the plugin actor.
          plugin-dispatcher = "pekko.actor.default-dispatcher"
        }
        //#journal-plugin-config

        //#snapshot-store-plugin-config
        # Path to the snapshot store plugin to be used
        pekko.persistence.snapshot-store.plugin = "my-snapshot-store"

        # My custom snapshot store plugin
        my-snapshot-store {
          # Class name of the plugin.
          class = "docs.persistence.MySnapshotStore"
          # Dispatcher for the plugin actor.
          plugin-dispatcher = "pekko.persistence.dispatchers.default-plugin-dispatcher"
        }
        //#snapshot-store-plugin-config
      """

    val system = ActorSystem(
      "PersistencePluginDocSpec",
      ConfigFactory
        .parseString(providerConfig)
        .withFallback(ConfigFactory.parseString(PersistencePluginDocSpec.config)))
    try {
      Persistence(system)
    } finally {
      TestKit.shutdownActorSystem(system, 10.seconds, false)
    }
  }
}

object SharedLeveldbPluginDocSpec {
  import org.apache.pekko.actor._
  import org.apache.pekko.persistence.journal.leveldb.SharedLeveldbJournal

  val config =
    """
      //#shared-journal-config
      pekko.persistence.journal.plugin = "pekko.persistence.journal.leveldb-shared"
      //#shared-journal-config
      //#shared-store-native-config
      pekko.persistence.journal.leveldb-shared.store.native = off
      //#shared-store-native-config
      //#shared-store-config
      pekko.persistence.journal.leveldb-shared.store.dir = "target/shared"
      //#shared-store-config
      //#event-adapter-config
      pekko.persistence.journal.leveldb-shared.adapter = "com.example.MyAdapter"
      //#event-adapter-config
    """

  // #shared-store-usage
  trait SharedStoreUsage extends Actor {
    override def preStart(): Unit = {
      context.actorSelection("akka://example@127.0.0.1:2552/user/store") ! Identify(1)
    }

    def receive = {
      case ActorIdentity(1, Some(store)) =>
        SharedLeveldbJournal.setStore(store, context.system)
    }
  }
  // #shared-store-usage
}

trait SharedLeveldbPluginDocSpec {
  val system: ActorSystem

  {
    import org.apache.pekko.actor._
    // #shared-store-creation
    import org.apache.pekko.persistence.journal.leveldb.SharedLeveldbStore

    val store = system.actorOf(Props[SharedLeveldbStore](), "store")
    // #shared-store-creation
  }
}

class MyJournal extends AsyncWriteJournal {
  // #sync-journal-plugin-api
  def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] =
    Future.fromTry(Try {
      // blocking call here
      ???
    })
  // #sync-journal-plugin-api

  def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = ???
  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
      replayCallback: (PersistentRepr) => Unit): Future[Unit] = ???
  def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = ???

  // optionally override:
  override def receivePluginInternal: Receive = super.receivePluginInternal
}

class MySnapshotStore extends SnapshotStore {
  def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = ???
  def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = ???
  def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = ???
  def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = ???

  // optionally override:
  override def receivePluginInternal: Receive = super.receivePluginInternal
}

object PersistenceTCKDoc {
  object example1 {
    import org.apache.pekko.persistence.journal.JournalSpec

    // #journal-tck-scala
    class MyJournalSpec
        extends JournalSpec(
          config = ConfigFactory.parseString("""pekko.persistence.journal.plugin = "my.journal.plugin"""")) {

      override def supportsRejectingNonSerializableObjects: CapabilityFlag =
        false // or CapabilityFlag.off

      override def supportsSerialization: CapabilityFlag =
        true // or CapabilityFlag.on
    }
    // #journal-tck-scala
  }
  object example2 {
    import org.apache.pekko.persistence.snapshot.SnapshotStoreSpec

    // #snapshot-store-tck-scala
    class MySnapshotStoreSpec
        extends SnapshotStoreSpec(
          config = ConfigFactory.parseString("""
        pekko.persistence.snapshot-store.plugin = "my.snapshot-store.plugin"
        """)) {

      override def supportsSerialization: CapabilityFlag =
        true // or CapabilityFlag.on
    }
    // #snapshot-store-tck-scala
  }
  object example3 {
    import java.io.File

    import org.apache.pekko.persistence.journal.JournalSpec
    import org.iq80.leveldb.util.FileUtils

    // #journal-tck-before-after-scala
    class MyJournalSpec
        extends JournalSpec(config = ConfigFactory.parseString("""
        pekko.persistence.journal.plugin = "my.journal.plugin"
        """)) {

      override def supportsRejectingNonSerializableObjects: CapabilityFlag =
        true // or CapabilityFlag.on

      val storageLocations = List(
        new File(system.settings.config.getString("pekko.persistence.journal.leveldb.dir")),
        new File(config.getString("pekko.persistence.snapshot-store.local.dir")))

      override def beforeAll(): Unit = {
        super.beforeAll()
        storageLocations.foreach(FileUtils.deleteRecursively)
      }

      override def afterAll(): Unit = {
        storageLocations.foreach(FileUtils.deleteRecursively)
        super.afterAll()
      }

    }
    // #journal-tck-before-after-scala
  }
}
