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

package org.apache.pekko.persistence.typed.state.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.state.RecoveryCompleted

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object DurableStateRevisionSpec {

  def conf: Config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    """))

}

class DurableStateRevisionSpec
    extends ScalaTestWithActorTestKit(DurableStateRevisionSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  private def behavior(pid: PersistenceId, probe: ActorRef[String]): Behavior[String] =
    Behaviors.setup(ctx =>
      DurableStateBehavior[String, String](
        pid,
        "",
        (state, command) =>
          state match {
            case "stashing" =>
              command match {
                case "unstash" =>
                  probe ! s"${DurableStateBehavior.lastSequenceNumber(ctx)} unstash"
                  Effect.persist("normal").thenUnstashAll()
                case _ =>
                  Effect.stash()
              }
            case _ =>
              command match {
                case "cmd" =>
                  probe ! s"${DurableStateBehavior.lastSequenceNumber(ctx)} onCommand"
                  Effect
                    .persist("state")
                    .thenRun(_ => probe ! s"${DurableStateBehavior.lastSequenceNumber(ctx)} thenRun")
                case "stash" =>
                  probe ! s"${DurableStateBehavior.lastSequenceNumber(ctx)} stash"
                  Effect.persist("stashing")
                case "snapshot" =>
                  Effect.persist("snapshot")
              }
          }).receiveSignal {
        case (_, RecoveryCompleted) =>
          probe ! s"${DurableStateBehavior.lastSequenceNumber(ctx)} onRecoveryComplete"
      })

  "The revision number" must {

    "be accessible in the handlers" in {
      val probe = TestProbe[String]()
      val ref = spawn(behavior(PersistenceId.ofUniqueId("pid-1"), probe.ref))
      probe.expectMessage("0 onRecoveryComplete")

      ref ! "cmd"
      probe.expectMessage("0 onCommand")
      probe.expectMessage("1 thenRun")

      ref ! "cmd"
      probe.expectMessage("1 onCommand")
      probe.expectMessage("2 thenRun")

      testKit.stop(ref)
      probe.expectTerminated(ref)

      // and during recovery
      val ref2 = spawn(behavior(PersistenceId.ofUniqueId("pid-1"), probe.ref))
      probe.expectMessage("2 onRecoveryComplete")

      ref2 ! "cmd"
      probe.expectMessage("2 onCommand")
      probe.expectMessage("3 thenRun")
    }

    "be available while unstashing" in {
      val probe = TestProbe[String]()
      val ref = spawn(behavior(PersistenceId.ofUniqueId("pid-2"), probe.ref))
      probe.expectMessage("0 onRecoveryComplete")

      ref ! "stash"
      ref ! "cmd"
      ref ! "cmd"
      ref ! "cmd"
      ref ! "unstash"
      probe.expectMessage("0 stash")
      probe.expectMessage("1 unstash")
      probe.expectMessage("2 onCommand")
      probe.expectMessage("3 thenRun")
      probe.expectMessage("3 onCommand")
      probe.expectMessage("4 thenRun")
      probe.expectMessage("4 onCommand")
      probe.expectMessage("5 thenRun")
    }

    "not fail when snapshotting" in {
      val probe = TestProbe[String]()
      val ref = spawn(behavior(PersistenceId.ofUniqueId("pid-3"), probe.ref))
      probe.expectMessage("0 onRecoveryComplete")

      ref ! "cmd"
      ref ! "snapshot"
      ref ! "cmd"

      probe.expectMessage("0 onCommand") // first command
      probe.expectMessage("1 thenRun")
      probe.expectMessage("2 onCommand") // second command
      probe.expectMessage("3 thenRun")
    }
  }
}
