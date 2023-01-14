/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import java.util.concurrent.CyclicBarrier

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Success

import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Extension
import pekko.actor.typed.ExtensionId
import pekko.actor.typed.scaladsl.adapter._
import pekko.persistence
import pekko.persistence.SelectedSnapshot
import pekko.persistence.snapshot.SnapshotStore
import pekko.persistence.typed.StashingWhenSnapshottingSpec.ControllableSnapshotStoreExt
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior

object StashingWhenSnapshottingSpec {
  object ControllableSnapshotStoreExt extends ExtensionId[ControllableSnapshotStoreExt] {

    override def createExtension(system: ActorSystem[_]): ControllableSnapshotStoreExt =
      new ControllableSnapshotStoreExt()
  }

  class ControllableSnapshotStoreExt extends Extension {
    val completeSnapshotWrite = Promise[Unit]()
    val snapshotWriteStarted = new CyclicBarrier(2)
  }

  class ControllableSnapshotStore extends SnapshotStore {
    override def loadAsync(
        persistenceId: String,
        criteria: persistence.SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = Future.successful(None)

    override def saveAsync(metadata: persistence.SnapshotMetadata, snapshot: Any): Future[Unit] = {
      ControllableSnapshotStoreExt(context.system.toTyped).snapshotWriteStarted.await()
      ControllableSnapshotStoreExt(context.system.toTyped).completeSnapshotWrite.future
    }
    override def deleteAsync(metadata: persistence.SnapshotMetadata): Future[Unit] = Future.successful(())
    override def deleteAsync(persistenceId: String, criteria: persistence.SnapshotSelectionCriteria): Future[Unit] =
      Future.successful(())
  }
  val config = ConfigFactory.parseString(s"""
  slow-snapshot {
    class = "org.apache.pekko.persistence.typed.StashingWhenSnapshottingSpec$$ControllableSnapshotStore"
  }
  pekko.actor.allow-java-serialization = on
  pekko {
    loglevel = "INFO"

    persistence {
      journal {
        plugin = "pekko.persistence.journal.inmem"
        auto-start-journals = []
      }

      snapshot-store {
        plugin = "slow-snapshot"
        auto-start-journals = []
      }
    }
  }
    """)

  def persistentTestBehavior(pid: PersistenceId, eventProbe: TestProbe[String]) =
    EventSourcedBehavior[String, String, List[String]](
      pid,
      Nil,
      (_, command) => Effect.persist(command),
      (state, event) => {
        eventProbe.ref.tell(event)
        event :: state
      }).snapshotWhen((_, event, _) => event.contains("snap"))
}

class StashingWhenSnapshottingSpec
    extends ScalaTestWithActorTestKit(StashingWhenSnapshottingSpec.config)
    with AnyWordSpecLike
    with LogCapturing {
  "A persistent actor" should {
    "stash messages and automatically replay when snapshot is in progress" in {
      val eventProbe = TestProbe[String]()
      val persistentActor =
        spawn(StashingWhenSnapshottingSpec.persistentTestBehavior(PersistenceId.ofUniqueId("1"), eventProbe))
      persistentActor ! "one"
      eventProbe.expectMessage("one")
      persistentActor ! "snap"
      eventProbe.expectMessage("snap")
      ControllableSnapshotStoreExt(system).snapshotWriteStarted.await()
      persistentActor ! "two"
      eventProbe.expectNoMessage() // snapshot in progress
      ControllableSnapshotStoreExt(system).completeSnapshotWrite.complete(Success(()))
      eventProbe.expectMessage("two")
    }
  }
}
