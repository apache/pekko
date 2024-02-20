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

package org.apache.pekko.persistence.typed.internal

import scala.concurrent.duration._

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.typed.internal.InternalProtocol.IncomingCommand
import pekko.persistence.typed.internal.InternalProtocol.RecoveryPermitGranted

class StashStateSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "StashState" should {

    "clear buffer on PostStop" in {
      val probe = TestProbe[Int]()
      val behavior = TestBehavior(probe)
      val ref = spawn(behavior)
      ref ! RecoveryPermitGranted
      ref ! RecoveryPermitGranted
      probe.expectMessage(1)
      probe.expectMessage(2)
      ref ! IncomingCommand("bye")
      probe.expectTerminated(ref, 100.millis)

      spawn(behavior) ! RecoveryPermitGranted
      probe.expectMessage(1)
    }
  }

  object TestBehavior {

    def apply(probe: TestProbe[Int]): Behavior[InternalProtocol] = {
      val settings = dummySettings()
      Behaviors.setup[InternalProtocol] { ctx =>
        val stashState = new StashState(ctx, settings)
        Behaviors
          .receiveMessagePartial[InternalProtocol] {
            case RecoveryPermitGranted =>
              stashState.internalStashBuffer.stash(RecoveryPermitGranted)
              probe.ref ! stashState.internalStashBuffer.size
              Behaviors.same[InternalProtocol]
            case _: IncomingCommand[?] => Behaviors.stopped
          }
          .receiveSignal {
            case (_, _) =>
              stashState.clearStashBuffers()
              Behaviors.stopped[InternalProtocol]
          }
      }
    }
  }

  private def dummySettings(capacity: Int = 42) =
    EventSourcedSettings(
      stashCapacity = capacity,
      stashOverflowStrategy = StashOverflowStrategy.Fail,
      logOnStashing = false,
      recoveryEventTimeout = 3.seconds,
      journalPluginId = "",
      snapshotPluginId = "",
      useContextLoggerForInternalLogging = false)

}
