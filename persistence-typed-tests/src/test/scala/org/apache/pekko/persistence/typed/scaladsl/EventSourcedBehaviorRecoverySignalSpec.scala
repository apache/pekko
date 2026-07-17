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

package org.apache.pekko.persistence.typed.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.concurrent.TrieMap
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Extension
import pekko.actor.typed.ExtensionId
import pekko.actor.typed.PostStop
import pekko.actor.typed.PreRestart
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.scaladsl.adapter._
import pekko.persistence.SelectedSnapshot
import pekko.persistence.SnapshotMetadata
import pekko.persistence.SnapshotSelectionCriteria
import pekko.persistence.journal.inmem.InmemJournal
import pekko.persistence.snapshot.SnapshotStore
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.RecoveryCompleted

import org.scalatest.wordspec.AnyWordSpecLike

object EventSourcedBehaviorRecoverySignalSpec {

  sealed trait ObservedSignal
  case object PostStopObserved extends ObservedSignal
  case object PreRestartObserved extends ObservedSignal
  case object RecoveryCompletedObserved extends ObservedSignal

  object BlockingSnapshotStoreControl extends ExtensionId[BlockingSnapshotStoreControl] {
    override def createExtension(system: ActorSystem[?]): BlockingSnapshotStoreControl =
      new BlockingSnapshotStoreControl
  }

  final class BlockingSnapshotStoreControl extends Extension {
    private val loadStarted = TrieMap.empty[String, Promise[Unit]]

    def expectLoad(persistenceId: String): Future[Unit] =
      loadStarted.getOrElseUpdate(persistenceId, Promise[Unit]()).future

    def markLoadStarted(persistenceId: String): Unit =
      loadStarted.getOrElseUpdate(persistenceId, Promise[Unit]()).trySuccess(())
  }

  final class BlockingSnapshotStore extends SnapshotStore {
    override def loadAsync(
        persistenceId: String,
        criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
      BlockingSnapshotStoreControl(context.system.toTyped).markLoadStarted(persistenceId)
      Promise[Option[SelectedSnapshot]]().future
    }

    override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
      Future.successful(())

    override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
      Future.successful(())

    override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] =
      Future.successful(())
  }

  val config = s"""
    pekko.persistence.max-concurrent-recoveries = 1
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    pekko.persistence.snapshot-store.plugin = "blocking-snapshot-store"

    blocking-snapshot-store.class = "${classOf[BlockingSnapshotStore].getName}"

    short-recovery-timeout-journal {
      class = "${classOf[InmemJournal].getName}"
      recovery-event-timeout = 100 millis
    }
    """

  def behavior(
      persistenceId: PersistenceId,
      signalProbe: ActorRef[ObservedSignal]): EventSourcedBehavior[String, String, String] =
    EventSourcedBehavior[String, String, String](
      persistenceId,
      emptyState = "",
      commandHandler = (_, _) => Effect.none,
      eventHandler = (state, _) => state).receiveSignal {
      case (_, PostStop)          => signalProbe ! PostStopObserved
      case (_, PreRestart)        => signalProbe ! PreRestartObserved
      case (_, RecoveryCompleted) => signalProbe ! RecoveryCompletedObserved
    }
}

class EventSourcedBehaviorRecoverySignalSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorRecoverySignalSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  import EventSourcedBehaviorRecoverySignalSpec._

  private val persistenceIdCounter = new AtomicInteger

  private def nextPersistenceId(): PersistenceId =
    PersistenceId.ofUniqueId(s"recovery-signal-${persistenceIdCounter.incrementAndGet()}")

  "An EventSourcedBehavior loading a snapshot" must {
    "invoke the PostStop handler and return its recovery permit when stopped" in {
      val blockedPersistenceId = nextPersistenceId()
      val loadStarted = BlockingSnapshotStoreControl(system).expectLoad(blockedPersistenceId.id)
      val blockedSignalProbe = TestProbe[ObservedSignal]()
      val blocked = spawn(behavior(blockedPersistenceId, blockedSignalProbe.ref))

      Await.result(loadStarted, 3.seconds)

      val queuedSignalProbe = TestProbe[ObservedSignal]()
      val queued =
        spawn(behavior(nextPersistenceId(), queuedSignalProbe.ref)
          .withSnapshotPluginId("pekko.persistence.no-snapshot-store"))
      queuedSignalProbe.expectNoMessage(100.millis)

      testKit.stop(blocked)

      blockedSignalProbe.expectMessage(PostStopObserved)
      queuedSignalProbe.expectMessage(RecoveryCompletedObserved)
      testKit.stop(queued)
    }

    "invoke the PreRestart handler when snapshot recovery restarts" in {
      val persistenceId = nextPersistenceId()
      val loadStarted = BlockingSnapshotStoreControl(system).expectLoad(persistenceId.id)
      val signalProbe = TestProbe[ObservedSignal]()
      val restarting =
        spawn(
          behavior(persistenceId, signalProbe.ref)
            .withJournalPluginId("short-recovery-timeout-journal")
            .onPersistFailure(
              SupervisorStrategy
                .restartWithBackoff(10.millis, 10.millis, randomFactor = 0.0)
                .withLoggingEnabled(false)))

      Await.result(loadStarted, 3.seconds)
      signalProbe.expectMessage(PreRestartObserved)
      testKit.stop(restarting)
    }
  }
}
