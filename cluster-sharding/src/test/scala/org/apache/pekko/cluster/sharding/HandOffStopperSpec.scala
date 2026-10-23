/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.cluster.sharding

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.cluster.sharding.ShardCoordinator.Internal.ShardStopped
import pekko.cluster.sharding.ShardRegion.HandOffStopper
import pekko.event.Logging
import pekko.testkit.{ ExplicitlyTriggeredScheduler, PekkoSpec, TestProbe }

object HandOffStopperSpec {
  case object StopMessage

  // does not handle the hand off stop message
  class IgnoringEntity extends Actor {
    override def receive: Receive = {
      case _ =>
    }
  }

  // blocks in postStop, so the HandOffStopper keeps running after it has stopped this entity
  class SlowStoppingEntity(release: CountDownLatch) extends Actor {
    override def receive: Receive = {
      case _ =>
    }

    override def postStop(): Unit = release.await(10, TimeUnit.SECONDS)
  }
}

class HandOffStopperSpec extends PekkoSpec("""
    pekko.scheduler.implementation = "org.apache.pekko.testkit.ExplicitlyTriggeredScheduler"
    """) {
  import HandOffStopperSpec._

  private def scheduler = system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler]

  private def warningsProbe(): TestProbe = {
    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[Logging.Warning])
    probe
  }

  private def expectWarning(warnings: TestProbe, texts: String*): Unit =
    warnings.fishForMessage(hint = texts.mkString(" ... ")) {
      case w: Logging.Warning => texts.forall(w.message.toString.contains)
    }

  "HandOffStopper" must {

    "report the remaining wait and stop the entities after the entity stop timeout" in {
      val warnings = warningsProbe()
      val replyTo = TestProbe()
      val entity = system.actorOf(Props[IgnoringEntity]())
      // a hand-off-timeout of 20s gives an entity stop timeout of 15s
      system.actorOf(HandOffStopper.props("typeName", "1", replyTo.ref, Set(entity), StopMessage, 20.seconds))

      scheduler.timePasses(5.seconds)
      expectWarning(
        warnings,
        "not stopped after [5 seconds]",
        "Waiting additional [10 seconds] before stopping the remaining entities")

      scheduler.timePasses(10.seconds)
      expectWarning(warnings, "after [15 seconds], stopping the remaining [1] entities")
      replyTo.expectMsg(ShardStopped("1"))
    }

    "not warn after the entity stop timeout has fired" in {
      val warnings = warningsProbe()
      val replyTo = TestProbe()
      val release = new CountDownLatch(1)
      val entity = system.actorOf(Props(new SlowStoppingEntity(release)))
      // a hand-off-timeout of 6s gives an entity stop timeout of 1s, which fires before the 5s warning
      system.actorOf(HandOffStopper.props("typeName", "2", replyTo.ref, Set(entity), StopMessage, 6.seconds))

      scheduler.timePasses(1.second)
      expectWarning(warnings, "after [1 second], stopping the remaining [1] entities")

      // the entity is still stopping, so the HandOffStopper is still running when the warning would be due
      scheduler.timePasses(4.seconds)
      release.countDown()
      replyTo.expectMsg(ShardStopped("2"))

      // a warning is published before the HandOffStopper handles the entity termination and replies
      warnings.receiveWhile(100.millis) {
        case w: Logging.Warning => w.message.toString
      }.filter(_.contains("not stopped after")) shouldBe empty
    }
  }
}
