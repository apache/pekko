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

package org.apache.pekko.actor.typed
package scaladsl

import scala.concurrent.ExecutionContextExecutor

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe

import org.scalatest.wordspec.AnyWordSpecLike

class ReceivePartialSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  implicit val ec: ExecutionContextExecutor = system.executionContext

  "An immutable partial" must {
    "correctly install the receiveMessage handler" in {
      val probe = TestProbe[Command]("probe")
      val behavior =
        Behaviors.receiveMessagePartial[Command] {
          case Command2 =>
            probe.ref ! Command2
            Behaviors.same
        }
      val actor = spawn(behavior)

      actor ! Command1
      probe.expectNoMessage()

      actor ! Command2
      probe.expectMessage(Command2)
    }

    "correctly install the receive handler" in {
      val probe = TestProbe[Command]("probe")
      val behavior =
        Behaviors.receivePartial[Command] {
          case (_, Command2) =>
            probe.ref ! Command2
            Behaviors.same
        }
      val actor = spawn(behavior)

      actor ! Command1
      probe.expectNoMessage()

      actor ! Command2
      probe.expectMessage(Command2)
    }
  }

  private sealed trait Command
  private case object Command1 extends Command
  private case object Command2 extends Command
}
