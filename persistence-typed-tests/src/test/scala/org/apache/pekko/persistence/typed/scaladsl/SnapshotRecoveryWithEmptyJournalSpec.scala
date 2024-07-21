/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.serialization.Snapshot
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.typed.PersistenceId
import pekko.serialization.Serialization
import pekko.serialization.SerializationExtension
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.File
import java.util.UUID

object SnapshotRecoveryWithEmptyJournalSpec {
  val survivingSnapshotPath = s"target/survivingSnapshotPath-${UUID.randomUUID().toString}"

  def conf: Config = PersistenceTestKitPlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
    pekko.persistence.snapshot-store.local.dir = "${SnapshotRecoveryWithEmptyJournalSpec.survivingSnapshotPath}"
    pekko.actor.allow-java-serialization = on
    pekko.actor.warn-about-java-serializer-usage = off
    """))

  object TestActor {
    def apply(name: String, probe: ActorRef[Any]): Behavior[String] =
      Behaviors.setup { context =>
        EventSourcedBehavior[String, String, List[String]](
          PersistenceId.ofUniqueId(name),
          Nil,
          (state, cmd) =>
            cmd match {
              case "get" =>
                probe ! state.reverse
                Effect.none
              case _ =>
                Effect.persist(s"$cmd-${EventSourcedBehavior.lastSequenceNumber(context) + 1}")
            },
          (state, event) => event :: state)
      }
  }

}

class SnapshotRecoveryWithEmptyJournalSpec
    extends ScalaTestWithActorTestKit(SnapshotRecoveryWithEmptyJournalSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {
  import SnapshotRecoveryWithEmptyJournalSpec._

  val snapshotsDir: File = new File(survivingSnapshotPath)

  val serializationExtension: Serialization = SerializationExtension(system)

  val persistenceId: String = system.name

  // Prepare a hand made snapshot file as basis for the recovery start point
  private def createSnapshotFile(sequenceNr: Long, ts: Long, data: Any): Unit = {
    val snapshotFile = new File(snapshotsDir, s"snapshot-$persistenceId-$sequenceNr-$ts")
    FileUtils.writeByteArrayToFile(snapshotFile, serializationExtension.serialize(Snapshot(data)).get)
  }

  val givenSnapshotSequenceNr: Long = 4711L
  val givenTimestamp: Long = 1000L

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    createSnapshotFile(givenSnapshotSequenceNr - 1, givenTimestamp - 1, List("a-1"))
    createSnapshotFile(givenSnapshotSequenceNr, givenTimestamp, List("b-2", "a-1"))
  }

  "A persistent actor in a system that only has snapshots and no previous journal activity" must {
    "recover its state and sequence number starting from the most recent snapshot and use subsequent sequence numbers to persist events to the journal" in {

      val probe = createTestProbe[Any]()
      val ref = spawn(TestActor(persistenceId, probe.ref))
      ref ! "c"
      ref ! "get"
      probe.expectMessage(List("a-1", "b-2", s"c-${givenSnapshotSequenceNr + 1}"))
    }
  }

}
