/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.testkit.ImplicitSender

object OptimizedRecoverySpec {

  object TestPersistentActor {
    case object TakeSnapshot
    final case class Save(s: String)
    final case class Saved(s: String, seqNr: Long)
    case object PersistFromRecoveryCompleted

    def props(name: String, recovery: Recovery, probe: ActorRef): Props = {
      Props(new TestPersistentActor(name, recovery, probe))
    }
  }

  class TestPersistentActor(name: String, override val recovery: Recovery, probe: ActorRef)
      extends NamedPersistentActor(name) {
    import TestPersistentActor._

    override def persistenceId: String = name

    var state = ""

    def receiveCommand = {
      case TakeSnapshot           => saveSnapshot(state)
      case s: SaveSnapshotSuccess => probe ! s
      case GetState               => probe ! state
      case Save(s)                =>
        persist(Saved(s, lastSequenceNr + 1)) { evt =>
          state += evt.s
          probe ! evt
        }
    }

    def receiveRecover = {
      case s: SnapshotOffer =>
        probe ! s
        state = s.snapshot.toString
      case evt: Saved =>
        state += evt.s
        probe ! evt

      case RecoveryCompleted =>
        require(!recoveryRunning, "expected !recoveryRunning in RecoveryCompleted")
        probe ! RecoveryCompleted
        // verify that persist can be used here
        persist(PersistFromRecoveryCompleted)(_ => probe ! PersistFromRecoveryCompleted)
    }
  }

}

class OptimizedRecoverySpec
    extends PersistenceSpec(PersistenceSpec.config("inmem", "OptimizedRecoverySpec"))
    with ImplicitSender {

  import OptimizedRecoverySpec.TestPersistentActor
  import OptimizedRecoverySpec.TestPersistentActor._

  def setup(persistenceId: String): ActorRef = {
    val ref = system.actorOf(TestPersistentActor.props(persistenceId, Recovery(), testActor))
    expectMsg(RecoveryCompleted)
    expectMsg(PersistFromRecoveryCompleted)
    ref ! Save("a")
    ref ! Save("b")
    expectMsg(Saved("a", 2))
    expectMsg(Saved("b", 3))
    ref ! TakeSnapshot
    expectMsgType[SaveSnapshotSuccess]
    ref ! Save("c")
    expectMsg(Saved("c", 4))
    ref ! GetState
    expectMsg("abc")
    ref
  }

  "Optimized recovery of persistent actor" must {
    "get RecoveryCompleted but no SnapshotOffer and events when Recovery.none" in {
      val persistenceId = "p1"
      setup(persistenceId)

      val ref = system.actorOf(TestPersistentActor.props(persistenceId, Recovery.none, testActor))
      expectMsg(RecoveryCompleted)
      expectMsg(PersistFromRecoveryCompleted)

      // and highest sequence number should be used, PersistFromRecoveryCompleted is 5
      ref ! Save("d")
      expectMsg(Saved("d", 6))
      ref ! GetState
      expectMsg("d")
    }

  }
}
