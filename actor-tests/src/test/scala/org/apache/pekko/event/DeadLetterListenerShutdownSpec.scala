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

package org.apache.pekko.event

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko

import com.typesafe.config.ConfigFactory

import pekko.Done
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.CoordinatedShutdown
import pekko.actor.DeadLetter
import pekko.actor.Props
import pekko.testkit.PekkoSpec
import pekko.testkit.TestDuration
import pekko.testkit.TestKit
import pekko.testkit.TestProbe

object DeadLetterListenerShutdownSpec {
  case object RunningMsg
  case object ShutdownMsg

  // A shutdown reason whose `terminate-actor-system` is overridden to `off` in the test config, so a
  // CoordinatedShutdown run for this reason executes its phases without terminating the system.
  case object NonTerminatingReason extends CoordinatedShutdown.Reason

  // Forwards only the DeadLetterListener's log events (identified by the shared "dead letters
  // encountered" phrase) so the assertions are not disturbed by other INFO log output produced
  // during shutdown (e.g. the CoordinatedShutdown "Running ..." message).
  class DeadLetterLogForwarder(target: ActorRef) extends Actor {
    def receive: Receive = {
      case info: Logging.Info if info.message.toString.contains("dead letters encountered") =>
        target ! info
      case _ => // ignore any other log event so it is not reported as an unhandled message
    }
  }
}

class DeadLetterListenerShutdownSpec extends PekkoSpec {
  import DeadLetterListenerShutdownSpec._

  private def newSystem(name: String, extraConfig: String): ActorSystem =
    ActorSystem(
      name,
      ConfigFactory
        .parseString(s"""
          pekko.log-dead-letters = on
          pekko.loglevel = INFO
          $extraConfig
        """)
        .withFallback(system.settings.config))

  private def collectorFor(sys: ActorSystem): TestProbe = {
    val collector = TestProbe()(sys)
    val forwarder = sys.actorOf(Props(new DeadLetterLogForwarder(collector.ref)))
    sys.eventStream.subscribe(forwarder, classOf[Logging.Info])
    collector
  }

  // Registers a task that pauses the first CoordinatedShutdown phase, then runs `during` while the
  // shutdown is paused (the system is mid-shutdown and the DeadLetterListener is still alive, since
  // the listener is only stopped in the last phase). `triggerShutdown` starts the shutdown
  // (`terminate()` or `run(...)`) and yields the future to await once the pause is released, so this
  // works for both terminating and non-terminating runs. The release always happens, even if an
  // assertion throws, so the system is never left blocked.
  private def whilePausedDuringShutdown(sys: ActorSystem, triggerShutdown: => Future[?])(during: => Unit): Unit = {
    val reached = Promise[Done]()
    val release = Promise[Done]()
    CoordinatedShutdown(sys).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "pause-for-test") { () =>
      reached.success(Done)
      release.future
    }
    val shutdown = triggerShutdown
    try {
      Await.result(reached.future, 5.seconds)
      during
    } finally {
      release.trySuccess(Done)
      Await.result(shutdown, 10.seconds)
    }
  }

  "DeadLetterListener" must {

    "not log dead letters during shutdown when log-dead-letters-during-shutdown is off (#3256)" in {
      val sys = newSystem("DeadLetterListenerShutdownSpec-off", "pekko.log-dead-letters-during-shutdown = off")
      try {
        val collector = collectorFor(sys)

        // While the system is running the dead letter is logged.
        sys.eventStream.publish(DeadLetter(RunningMsg, sys.deadLetters, sys.deadLetters))
        collector.expectMsgType[Logging.Info](3.seconds).message.toString should include("RunningMsg")

        whilePausedDuringShutdown(sys, sys.terminate()) {
          // A dead letter produced while terminating must not be logged. The window is dilated so the
          // regression is still caught on a slow CI box (a reintroduced bug would log it here).
          sys.eventStream.publish(DeadLetter(ShutdownMsg, sys.deadLetters, sys.deadLetters))
          collector.expectNoMessage(1.second.dilated)
        }
      } finally if (!sys.whenTerminated.isCompleted) TestKit.shutdownActorSystem(sys)
    }

    "still log dead letters during shutdown when log-dead-letters-during-shutdown is on" in {
      val sys = newSystem("DeadLetterListenerShutdownSpec-on", "pekko.log-dead-letters-during-shutdown = on")
      try {
        val collector = collectorFor(sys)

        whilePausedDuringShutdown(sys, sys.terminate()) {
          // With shutdown logging enabled the dead letter is still logged while terminating.
          sys.eventStream.publish(DeadLetter(ShutdownMsg, sys.deadLetters, sys.deadLetters))
          collector.expectMsgType[Logging.Info](3.seconds).message.toString should include("ShutdownMsg")
        }
      } finally if (!sys.whenTerminated.isCompleted) TestKit.shutdownActorSystem(sys)
    }

    "still log dead letters during a coordinated shutdown that does not terminate the system (#3256)" in {
      // With terminate-actor-system = off a CoordinatedShutdown.run() executes its phases but keeps
      // the system alive, leaving a shutdown reason set even though the system is not terminating.
      // Dead-letter logging must keep working in that case rather than being silently disabled for
      // the remaining lifetime of the still-running system.
      val sys = newSystem(
        "DeadLetterListenerShutdownSpec-nonterminating",
        """
          pekko.log-dead-letters-during-shutdown = off
          pekko.coordinated-shutdown.terminate-actor-system = off
          pekko.coordinated-shutdown.run-by-actor-system-terminate = off
        """)
      try {
        val collector = collectorFor(sys)

        whilePausedDuringShutdown(sys, CoordinatedShutdown(sys).run(CoordinatedShutdown.UnknownReason)) {
          sys.eventStream.publish(DeadLetter(ShutdownMsg, sys.deadLetters, sys.deadLetters))
          collector.expectMsgType[Logging.Info](3.seconds).message.toString should include("ShutdownMsg")
        }
      } finally TestKit.shutdownActorSystem(sys)
    }

    "still log dead letters when a shutdown reason overrides terminate-actor-system to off (#3256)" in {
      // The base config terminates the system, but the specific shutdown reason overrides
      // terminate-actor-system to off, so this run does NOT terminate the system. The effective
      // (per-reason) value must be honored, not the base setting, otherwise logging would be
      // silently suppressed forever on a still-running system.
      val reasonClass = NonTerminatingReason.getClass.getName
      val sys = newSystem(
        "DeadLetterListenerShutdownSpec-reason-override",
        "pekko.log-dead-letters-during-shutdown = off\n" +
        "pekko.coordinated-shutdown.run-by-actor-system-terminate = off\n" +
        "pekko.coordinated-shutdown.reason-overrides.\"" + reasonClass + "\".terminate-actor-system = off")
      try {
        val collector = collectorFor(sys)

        whilePausedDuringShutdown(sys, CoordinatedShutdown(sys).run(NonTerminatingReason)) {
          sys.eventStream.publish(DeadLetter(ShutdownMsg, sys.deadLetters, sys.deadLetters))
          collector.expectMsgType[Logging.Info](3.seconds).message.toString should include("ShutdownMsg")
        }

        // The run has completed but the system is still alive and its shutdown reason stays set;
        // logging must keep working for the rest of the system's life, not be suppressed forever.
        sys.eventStream.publish(DeadLetter(ShutdownMsg, sys.deadLetters, sys.deadLetters))
        collector.expectMsgType[Logging.Info](3.seconds).message.toString should include("ShutdownMsg")
      } finally TestKit.shutdownActorSystem(sys)
    }
  }
}
