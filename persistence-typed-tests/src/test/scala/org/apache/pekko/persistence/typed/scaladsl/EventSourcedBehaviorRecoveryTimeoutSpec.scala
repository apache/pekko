/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.RecoveryTimedOut
import pekko.persistence.journal.SteppingInmemJournal
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.RecoveryFailed
import pekko.persistence.typed.internal.JournalFailureException

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object EventSourcedBehaviorRecoveryTimeoutSpec {

  val journalId = "event-sourced-behavior-recovery-timeout-spec"

  def config: Config =
    SteppingInmemJournal
      .config(journalId)
      .withFallback(ConfigFactory.parseString("""
        pekko.persistence.journal.stepping-inmem.recovery-event-timeout=1s
        """))
      .withFallback(ConfigFactory.parseString(s"""
        pekko.loglevel = INFO
        """))

  def testBehavior(persistenceId: PersistenceId, probe: ActorRef[AnyRef]): Behavior[String] =
    Behaviors.setup { _ =>
      EventSourcedBehavior[String, String, String](
        persistenceId,
        emptyState = "",
        commandHandler = (_, command) => Effect.persist(command).thenRun(_ => probe ! command),
        eventHandler = (state, evt) => state + evt).receiveSignal {
        case (_, RecoveryFailed(cause)) =>
          probe ! cause
      }
    }

}

class EventSourcedBehaviorRecoveryTimeoutSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorRecoveryTimeoutSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  import EventSourcedBehaviorRecoveryTimeoutSpec._

  val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()})")

  import org.apache.pekko.actor.typed.scaladsl.adapter._
  // needed for SteppingInmemJournal.step
  private implicit val classicSystem: pekko.actor.ActorSystem = system.toClassic

  "The recovery timeout" must {

    "fail recovery if timeout is not met when recovering" in {
      val probe = createTestProbe[AnyRef]()
      val pid = nextPid()
      val persisting = spawn(testBehavior(pid, probe.ref))

      probe.awaitAssert(SteppingInmemJournal.getRef(journalId), 3.seconds)
      val journal = SteppingInmemJournal.getRef(journalId)

      // initial read highest
      SteppingInmemJournal.step(journal)

      persisting ! "A"
      SteppingInmemJournal.step(journal)
      probe.expectMessage("A")

      testKit.stop(persisting)
      probe.expectTerminated(persisting)

      // now replay, but don't give the journal any tokens to replay events
      // so that we cause the timeout to trigger
      LoggingTestKit
        .error[JournalFailureException]
        .withMessageRegex("Exception during recovery.*Replay timed out")
        .expect {
          val replaying = spawn(testBehavior(pid, probe.ref))

          // initial read highest
          SteppingInmemJournal.step(journal)

          probe.expectMessageType[RecoveryTimedOut]
          probe.expectTerminated(replaying)
        }

      // avoid having it stuck in the next test from the
      // last read request above
      SteppingInmemJournal.step(journal)
    }

  }
}
