/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.RecoveryCompleted
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

object EventSourcedSequenceNumberSpec {

  private val conf = ConfigFactory.parseString(s"""
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.journal.inmem.test-serialization = on
      pekko.persistence.snapshot-store.plugin = "slow-snapshot-store"
      slow-snapshot-store.class = "${classOf[SlowInMemorySnapshotStore].getName}"
    """)

}

class EventSourcedSequenceNumberSpec
    extends ScalaTestWithActorTestKit(EventSourcedSequenceNumberSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  private def behavior(pid: PersistenceId, probe: ActorRef[String]): Behavior[String] =
    Behaviors.setup(ctx =>
      EventSourcedBehavior[String, String, String](pid, "",
        (state, command) =>
          state match {
            case "stashing" =>
              command match {
                case "unstash" =>
                  probe ! s"${EventSourcedBehavior.lastSequenceNumber(ctx)} unstash"
                  Effect.persist("normal").thenUnstashAll()
                case _ =>
                  Effect.stash()
              }
            case _ =>
              command match {
                case "cmd" =>
                  probe ! s"${EventSourcedBehavior.lastSequenceNumber(ctx)} onCommand"
                  Effect
                    .persist("evt")
                    .thenRun(_ => probe ! s"${EventSourcedBehavior.lastSequenceNumber(ctx)} thenRun")
                case "cmd3" =>
                  probe ! s"${EventSourcedBehavior.lastSequenceNumber(ctx)} onCommand"
                  Effect
                    .persist("evt1", "evt2", "evt3")
                    .thenRun(_ => probe ! s"${EventSourcedBehavior.lastSequenceNumber(ctx)} thenRun")
                case "stash" =>
                  probe ! s"${EventSourcedBehavior.lastSequenceNumber(ctx)} stash"
                  Effect.persist("stashing")
                case "snapshot" =>
                  Effect.persist("snapshot")
              }
          },
        { (_, evt) =>
          probe ! s"${EventSourcedBehavior.lastSequenceNumber(ctx)} eventHandler $evt"
          evt
        }).snapshotWhen((_, event, _) => event == "snapshot").receiveSignal {
        case (_, RecoveryCompleted) =>
          probe ! s"${EventSourcedBehavior.lastSequenceNumber(ctx)} onRecoveryComplete"
      })

  "The sequence number" must {

    "be accessible in the handlers" in {
      val probe = TestProbe[String]()
      val ref = spawn(behavior(PersistenceId.ofUniqueId("ess-1"), probe.ref))
      probe.expectMessage("0 onRecoveryComplete")

      ref ! "cmd"
      probe.expectMessage("0 onCommand")
      probe.expectMessage("1 eventHandler evt")
      probe.expectMessage("1 thenRun")

      ref ! "cmd"
      probe.expectMessage("1 onCommand")
      probe.expectMessage("2 eventHandler evt")
      probe.expectMessage("2 thenRun")

      ref ! "cmd3"
      probe.expectMessage("2 onCommand")
      probe.expectMessage("3 eventHandler evt1")
      probe.expectMessage("4 eventHandler evt2")
      probe.expectMessage("5 eventHandler evt3")
      probe.expectMessage("5 thenRun")

      testKit.stop(ref)
      probe.expectTerminated(ref)

      // and during replay
      val ref2 = spawn(behavior(PersistenceId.ofUniqueId("ess-1"), probe.ref))
      probe.expectMessage("1 eventHandler evt")
      probe.expectMessage("2 eventHandler evt")
      probe.expectMessage("3 eventHandler evt1")
      probe.expectMessage("4 eventHandler evt2")
      probe.expectMessage("5 eventHandler evt3")
      probe.expectMessage("5 onRecoveryComplete")

      ref2 ! "cmd"
      probe.expectMessage("5 onCommand")
      probe.expectMessage("6 eventHandler evt")
      probe.expectMessage("6 thenRun")
    }

    "be available while unstashing" in {
      val probe = TestProbe[String]()
      val ref = spawn(behavior(PersistenceId.ofUniqueId("ess-2"), probe.ref))
      probe.expectMessage("0 onRecoveryComplete")

      ref ! "stash"
      ref ! "cmd"
      ref ! "cmd"
      ref ! "cmd3"
      ref ! "unstash"
      probe.expectMessage("0 stash")
      probe.expectMessage("1 eventHandler stashing")
      probe.expectMessage("1 unstash")
      probe.expectMessage("2 eventHandler normal")
      probe.expectMessage("2 onCommand")
      probe.expectMessage("3 eventHandler evt")
      probe.expectMessage("3 thenRun")
      probe.expectMessage("3 onCommand")
      probe.expectMessage("4 eventHandler evt")
      probe.expectMessage("4 thenRun")
      probe.expectMessage("4 onCommand") // cmd3
      probe.expectMessage("5 eventHandler evt1")
      probe.expectMessage("6 eventHandler evt2")
      probe.expectMessage("7 eventHandler evt3")
      probe.expectMessage("7 thenRun")
    }

    // reproducer for #27935
    "not fail when snapshotting" in {
      val probe = TestProbe[String]()
      val ref = spawn(behavior(PersistenceId.ofUniqueId("ess-3"), probe.ref))
      probe.expectMessage("0 onRecoveryComplete")

      ref ! "cmd"
      ref ! "snapshot"
      ref ! "cmd"

      probe.expectMessage("0 onCommand") // first command
      probe.expectMessage("1 eventHandler evt")
      probe.expectMessage("1 thenRun")
      probe.expectMessage("2 eventHandler snapshot")
      probe.expectMessage("2 onCommand") // second command
      probe.expectMessage("3 eventHandler evt")
      probe.expectMessage("3 thenRun")
    }
  }
}
