/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.SnapshotCompleted
import pekko.persistence.typed.SnapshotFailed
import pekko.serialization.jackson.CborSerializable
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicInteger

object SnapshotMutableStateSpec {

  def conf: Config = PersistenceTestKitPlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    pekko.persistence.snapshot-store.plugin = "slow-snapshot-store"

    slow-snapshot-store.class = "${classOf[SlowInMemorySnapshotStore].getName}"
    """))

  sealed trait Command extends CborSerializable
  case object Increment extends Command
  final case class GetValue(replyTo: ActorRef[Int]) extends Command

  sealed trait Event extends CborSerializable
  case object Incremented extends Event

  final class MutableState(var value: Int) extends CborSerializable

  def counter(
      persistenceId: PersistenceId,
      probe: ActorRef[String]): EventSourcedBehavior[Command, Event, MutableState] =
    EventSourcedBehavior[Command, Event, MutableState](
      persistenceId,
      emptyState = new MutableState(0),
      commandHandler = (state, cmd) =>
        cmd match {
          case Increment =>
            Effect.persist(Incremented)

          case GetValue(replyTo) =>
            replyTo ! state.value
            Effect.none
        },
      eventHandler = (state, evt) =>
        evt match {
          case Incremented =>
            state.value += 1
            probe ! s"incremented-${state.value}"
            state
        }).receiveSignal {
      case (_, SnapshotCompleted(meta)) =>
        probe ! s"snapshot-success-${meta.sequenceNr}"
      case (_, SnapshotFailed(meta, _)) =>
        probe ! s"snapshot-failure-${meta.sequenceNr}"
    }

}

class SnapshotMutableStateSpec
    extends ScalaTestWithActorTestKit(SnapshotMutableStateSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  import SnapshotMutableStateSpec._

  val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()})")

  "A typed persistent actor with mutable state" must {

    "support mutable state by stashing commands while storing snapshot" in {
      val pid = nextPid()
      val probe = TestProbe[String]()
      def snapshotState3: Behavior[Command] =
        counter(pid, probe.ref).snapshotWhen { (state, _, _) =>
          state.value == 3
        }
      val c = spawn(snapshotState3)

      (1 to 5).foreach { n =>
        c ! Increment
        probe.expectMessage(s"incremented-$n")
        if (n == 3) {
          // incremented-4 shouldn't be before the snapshot-success-3, because Increment 4 is stashed
          probe.expectMessage(s"snapshot-success-3")
        }
      }

      val replyProbe = TestProbe[Int]()
      c ! GetValue(replyProbe.ref)
      replyProbe.expectMessage(5)

      // recover new instance
      val c2 = spawn(snapshotState3)
      // starting from snapshot 3
      probe.expectMessage(s"incremented-4")
      probe.expectMessage(s"incremented-5")
      c2 ! GetValue(replyProbe.ref)
      replyProbe.expectMessage(5)
    }
  }
}
