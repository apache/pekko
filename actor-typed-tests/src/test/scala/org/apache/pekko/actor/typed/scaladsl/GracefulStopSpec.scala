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

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe

final class GracefulStopSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "Graceful stop" must {

    "properly stop the children and perform the cleanup" in {
      val probe = TestProbe[String]("probe")

      val behavior =
        Behaviors.setup[pekko.NotUsed] { context =>
          context.spawn[NotUsed](Behaviors.receiveSignal {
              case (_, PostStop) =>
                probe.ref ! "child-done"
                Behaviors.stopped
            }, "child1")

          context.spawn[NotUsed](Behaviors.receiveSignal {
              case (_, PostStop) =>
                probe.ref ! "child-done"
                Behaviors.stopped
            }, "child2")

          Behaviors.stopped { () =>
            // cleanup function body
            probe.ref ! "parent-done"
          }

        }

      spawn(behavior)
      probe.expectMessage("child-done")
      probe.expectMessage("child-done")
      probe.expectMessage("parent-done")
    }

    "properly perform the cleanup and stop itself for no children case" in {
      val probe = TestProbe[Done]("probe")

      val behavior =
        Behaviors.setup[pekko.NotUsed] { _ =>
          // do not spawn any children
          Behaviors.stopped { () =>
            // cleanup function body
            probe.ref ! Done
          }
        }

      spawn(behavior)
      probe.expectMessage(Done)
    }
  }

}
