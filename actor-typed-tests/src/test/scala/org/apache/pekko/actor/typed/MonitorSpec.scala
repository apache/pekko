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

package org.apache.pekko.actor.typed

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.scaladsl.Behaviors

class MonitorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "The monitor behavior" should {

    "monitor messages" in {
      val probe = TestProbe[String]()

      val beh: Behavior[String] = Behaviors.monitor(probe.ref, Behaviors.receiveMessage(_ => Behaviors.same))
      val ref: ActorRef[String] = spawn(beh)

      ref ! "message"

      probe.expectMessage("message")
    }

    "monitor messages once per ref initially" in {
      val probe = TestProbe[String]()

      def monitor(beh: Behavior[String]): Behavior[String] =
        Behaviors.monitor(probe.ref, beh)

      val beh: Behavior[String] =
        monitor(monitor(Behaviors.receiveMessage(_ => Behaviors.same)))
      val ref: ActorRef[String] = spawn(beh)

      ref ! "message 1"
      probe.expectMessage("message 1")
      ref ! "message 2"
      probe.expectMessage("message 2")
    }

    "monitor messages once per ref recursively" in {
      val probe = TestProbe[String]()

      def monitor(beh: Behavior[String]): Behavior[String] =
        Behaviors.monitor(probe.ref, beh)

      def next: Behavior[String] =
        monitor(Behaviors.receiveMessage(_ => next))
      val ref: ActorRef[String] = spawn(next)

      ref ! "message 1"
      probe.expectMessage("message 1")
      ref ! "message 2"
      probe.expectMessage("message 2")
      ref ! "message 3"
      probe.expectMessage("message 3")
    }

  }

}
