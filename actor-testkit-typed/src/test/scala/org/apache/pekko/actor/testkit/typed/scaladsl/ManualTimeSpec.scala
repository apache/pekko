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

package org.apache.pekko.actor.testkit.typed.scaladsl

import java.time.{ Duration => JDuration }
import java.util.concurrent.{ CountDownLatch, TimeUnit }

import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.ManualTimeSpec.config
import pekko.actor.typed.DispatcherSelector
import pekko.actor.typed.scaladsl.Behaviors

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object ManualTimeSpec {
  val config = ConfigFactory
    .parseString("""
      manual-time-test-dispatcher {
        type = Dispatcher
        executor = "thread-pool-executor"
        thread-pool-executor.fixed-pool-size = 1
        throughput = 1
      }
      pekko.actor.testkit.typed.expect-no-message-default = 3 s
    """)
    .withFallback(ManualTime.config)
}

class ManualTimeSpec extends ScalaTestWithActorTestKit(config) with AnyWordSpecLike with LogCapturing {

  private val manualTime = ManualTime()

  private def verifyDelayedMessageIsDetected(expectNoMessageFor: TestProbe[String] => Unit): Unit = {
    val target = TestProbe[String]()
    val timerStarted = TestProbe[Done]()
    val dispatcherSelector = DispatcherSelector.fromConfig("manual-time-test-dispatcher")

    spawn(
      Behaviors.withTimers[String] { timers =>
        timers.startSingleTimer("unexpected", 1.milli)
        timerStarted.ref ! Done
        Behaviors.receiveMessage { message =>
          target.ref ! message
          Behaviors.same
        }
      },
      dispatcherSelector)
    timerStarted.expectMessage(Done)

    val dispatcherBlocked = new CountDownLatch(1)
    val releaseDispatcher = new CountDownLatch(1)
    val blocker = spawn(
      Behaviors.receiveMessage[Done] { _ =>
        dispatcherBlocked.countDown()
        releaseDispatcher.await()
        Behaviors.stopped
      },
      dispatcherSelector)
    blocker ! Done
    dispatcherBlocked.await(3, TimeUnit.SECONDS) shouldBe true

    val testThread = Thread.currentThread()
    val watcherDone = new CountDownLatch(1)
    system.scheduler.scheduleOnce(
      2.millis,
      () => {
        val watcher = new Thread(
          () => {
            try {
              while (releaseDispatcher.getCount != 0 && testThread.getState != Thread.State.TIMED_WAITING)
                Thread.onSpinWait()
              if (testThread.getState == Thread.State.TIMED_WAITING)
                releaseDispatcher.countDown()
            } finally watcherDone.countDown()
          },
          "manual-time-expect-no-message-watcher")
        watcher.setDaemon(true)
        watcher.start()
      })(system.executionContext)

    try {
      intercept[AssertionError] {
        expectNoMessageFor(target)
      }.getMessage should include("Received unexpected message unexpected")
    } finally {
      releaseDispatcher.countDown()
      watcherDone.await(3, TimeUnit.SECONDS) shouldBe true
    }
  }

  "ManualTime.expectNoMessageFor" must {
    "detect a delayed message with the Scala DSL" in {
      verifyDelayedMessageIsDetected(probe => manualTime.expectNoMessageFor(2.millis, probe))
    }

    "detect a delayed message with the Java DSL" in {
      val javaManualTime = pekko.actor.testkit.typed.javadsl.ManualTime.get(system)
      verifyDelayedMessageIsDetected(probe =>
        javaManualTime.expectNoMessageFor(JDuration.ofMillis(2), probe.asJava))
    }
  }
}
