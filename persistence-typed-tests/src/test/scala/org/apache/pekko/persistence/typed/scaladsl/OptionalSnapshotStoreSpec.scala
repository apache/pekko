/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.LoggingTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.EventSourcedBehavior.CommandHandler
import pekko.serialization.jackson.CborSerializable
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

object OptionalSnapshotStoreSpec {

  sealed trait Command extends CborSerializable

  object AnyCommand extends Command

  final case class State(internal: String = UUID.randomUUID().toString) extends CborSerializable

  case class Event(id: Long = System.currentTimeMillis()) extends CborSerializable

  def persistentBehavior(probe: TestProbe[State], name: String = UUID.randomUUID().toString) =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(name),
      emptyState = State(),
      commandHandler = CommandHandler.command { _ =>
        Effect.persist(Event()).thenRun(probe.ref ! _)
      },
      eventHandler = {
        case (_, _) => State()
      }).snapshotWhen { case _ => true }

  def persistentBehaviorWithSnapshotPlugin(probe: TestProbe[State]) =
    persistentBehavior(probe).withSnapshotPluginId("pekko.persistence.snapshot-store.local")

}

class OptionalSnapshotStoreSpec extends ScalaTestWithActorTestKit(s"""
    pekko.persistence.publish-plugin-commands = on
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    pekko.persistence.journal.inmem.test-serialization = on

    # snapshot store plugin is NOT defined, things should still work
    pekko.persistence.snapshot-store.local.dir = "target/snapshots-${classOf[OptionalSnapshotStoreSpec].getName}/"
    """) with AnyWordSpecLike with LogCapturing {

  import OptionalSnapshotStoreSpec._

  "Persistence extension" must {
    "initialize properly even in absence of configured snapshot store" in {
      LoggingTestKit.warn("No default snapshot store configured").expect {
        val stateProbe = TestProbe[State]()
        spawn(persistentBehavior(stateProbe))
        stateProbe.expectNoMessage()
      }
    }

    "fail if PersistentActor tries to saveSnapshot without snapshot-store available" in {
      LoggingTestKit.error("No snapshot store configured").expect {
        LoggingTestKit.warn("Failed to save snapshot").expect {
          val stateProbe = TestProbe[State]()
          val persistentActor = spawn(persistentBehavior(stateProbe))
          persistentActor ! AnyCommand
          stateProbe.expectMessageType[State]
        }
      }
    }

    "successfully save a snapshot when no default snapshot-store configured, yet PersistentActor picked one explicitly" in {
      val stateProbe = TestProbe[State]()
      val persistentActor = spawn(persistentBehaviorWithSnapshotPlugin(stateProbe))
      persistentActor ! AnyCommand
      stateProbe.expectMessageType[State]
    }
  }
}
