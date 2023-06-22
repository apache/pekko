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
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.PersistenceTestKitSnapshotPlugin
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.SnapshotAdapter
import pekko.serialization.jackson.CborSerializable
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicInteger

object EventSourcedSnapshotAdapterSpec {

  case class State(s: String) extends CborSerializable
  case class Command(c: String) extends CborSerializable
  case class Event(e: String) extends CborSerializable
  case class PersistedState(s: String) extends CborSerializable
}

class EventSourcedSnapshotAdapterSpec
    extends ScalaTestWithActorTestKit(
      PersistenceTestKitPlugin.config.withFallback(PersistenceTestKitSnapshotPlugin.config))
    with AnyWordSpecLike
    with LogCapturing {
  import EventSourcedSnapshotAdapterSpec._
  import pekko.actor.typed.scaladsl.adapter._

  val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()})")

  val queries: PersistenceTestKitReadJournal =
    PersistenceQuery(system.toClassic)
      .readJournalFor[PersistenceTestKitReadJournal](PersistenceTestKitReadJournal.Identifier)

  private def behavior(pid: PersistenceId, probe: ActorRef[State]): EventSourcedBehavior[Command, Event, State] =
    EventSourcedBehavior[Command, Event, State](
      pid,
      State(""),
      commandHandler = { (state, command) =>
        command match {
          case Command(c) if c == "shutdown" =>
            Effect.stop()
          case Command(c) if c == "get" =>
            probe.tell(state)
            Effect.none
          case _ =>
            Effect.persist(Event(command.c)).thenRun(newState => probe ! newState)
        }
      },
      eventHandler = { (state, evt) =>
        state.copy(s = state.s + "|" + evt.e)
      })

  "Snapshot adapter" must {

    "adapt snapshots to any" in {
      val pid = nextPid()
      val stateProbe = TestProbe[State]()
      val snapshotFromJournal = TestProbe[PersistedState]()
      val snapshotToJournal = TestProbe[State]()
      val b = behavior(pid, stateProbe.ref)
        .snapshotAdapter(new SnapshotAdapter[State]() {
          override def toJournal(state: State): Any = {
            snapshotToJournal.ref.tell(state)
            PersistedState(state.s)
          }
          override def fromJournal(from: Any): State = from match {
            case ps: PersistedState =>
              snapshotFromJournal.ref.tell(ps)
              State(ps.s)
            case unexpected => throw new RuntimeException(s"Unexpected: $unexpected")
          }
        })
        .snapshotWhen { (_, event, _) =>
          event.e.contains("snapshot")
        }

      val ref = spawn(b)

      ref.tell(Command("one"))
      stateProbe.expectMessage(State("|one"))
      ref.tell(Command("snapshot now"))
      stateProbe.expectMessage(State("|one|snapshot now"))
      snapshotToJournal.expectMessage(State("|one|snapshot now"))
      ref.tell(Command("shutdown"))

      val ref2 = spawn(b)
      snapshotFromJournal.expectMessage(PersistedState("|one|snapshot now"))
      ref2.tell(Command("get"))
      stateProbe.expectMessage(State("|one|snapshot now"))
    }

  }
}
