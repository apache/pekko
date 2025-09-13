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

package org.apache.pekko.persistence.typed.scaladsl

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import org.apache.pekko
import pekko.actor.testkit.typed.TestException
import pekko.actor.testkit.typed.TestKitSettings
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.PostStop
import pekko.actor.typed.PreRestart
import pekko.actor.typed.Signal
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._
import pekko.persistence.AtomicWrite
import pekko.persistence.journal.inmem.InmemJournal
import pekko.persistence.typed.EventRejectedException
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.RecoveryCompleted
import pekko.persistence.typed.RecoveryFailed
import pekko.persistence.typed.internal.JournalFailureException

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class ChaosJournal extends InmemJournal {
  var counts = Map.empty[String, Int]
  var failRecovery = true
  var reject = true

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    val pid = messages.head.persistenceId
    counts = counts.updated(pid, counts.getOrElse(pid, 0) + 1)
    if (pid == "fail-first-2" && counts(pid) <= 2) {
      Future.failed(TestException("database says no"))
    } else if (pid.startsWith("fail-fifth") && counts(pid) == 5) {
      Future.failed(TestException("database says no"))
    } else if (pid == "reject-first" && reject) {
      reject = false
      Future.successful(messages.map(_ =>
        Try {
          throw TestException("I don't like it")
        }))
    } else if (messages.head.payload.head.payload == "malicious") {
      super.asyncWriteMessages(List(AtomicWrite(List(messages.head.payload.head.withPayload("wrong-event")))))
    } else {
      super.asyncWriteMessages(messages)
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    if (persistenceId == "fail-recovery-once" && failRecovery) {
      failRecovery = false
      Future.failed(TestException("Nah"))
    } else if (persistenceId == "fail-recovery") {
      Future.failed(TestException("Nope"))
    } else {
      super.asyncReadHighestSequenceNr(persistenceId, fromSequenceNr)
    }
  }
}

object EventSourcedBehaviorFailureSpec {

  val conf: Config = ConfigFactory.parseString(s"""
      pekko.loglevel = INFO
      pekko.persistence.journal.plugin = "failure-journal"
      failure-journal = $${pekko.persistence.journal.inmem}
      failure-journal {
        class = "org.apache.pekko.persistence.typed.scaladsl.ChaosJournal"
      }
    """).withFallback(ConfigFactory.defaultReference()).resolve()
}

class EventSourcedBehaviorFailureSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorFailureSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  implicit val testSettings: TestKitSettings = TestKitSettings(system)

  def failingPersistentActor(
      pid: PersistenceId,
      probe: ActorRef[String],
      additionalSignalHandler: PartialFunction[(String, Signal), Unit] = PartialFunction.empty)
      : EventSourcedBehavior[String, String, String] =
    EventSourcedBehavior[String, String, String](
      pid,
      "",
      (_, cmd) => {
        if (cmd == "wrong")
          throw TestException("wrong command")
        probe.tell("persisting")
        Effect.persist(cmd).thenRun { _ =>
          probe.tell("persisted")
          if (cmd == "wrong-callback") throw TestException("wrong command")
        }
      },
      (state, event) => {
        if (event == "wrong-event")
          throw TestException("wrong event")
        probe.tell(event)
        state + event
      }).receiveSignal(additionalSignalHandler.orElse {
      case (_, RecoveryCompleted) =>
        probe.tell("starting")
      case (_, PostStop) =>
        probe.tell("stopped")
      case (_, PreRestart) =>
        probe.tell("restarting")
    })

  "A typed persistent actor (failures)" must {

    "signal RecoveryFailure when replay fails" in {
      LoggingTestKit.error[JournalFailureException].expect {
        val probe = TestProbe[String]()
        val excProbe = TestProbe[Throwable]()
        spawn(failingPersistentActor(PersistenceId.ofUniqueId("fail-recovery"), probe.ref,
          {
            case (_, RecoveryFailed(t)) =>
              excProbe.ref ! t
          }))

        excProbe.expectMessageType[TestException].message shouldEqual "Nope"
        probe.expectMessage("stopped")
      }
    }

    "handle exceptions from RecoveryFailed signal handler" in {
      val probe = TestProbe[String]()
      val pa = spawn(failingPersistentActor(PersistenceId.ofUniqueId("fail-recovery-twice"), probe.ref,
        {
          case (_, RecoveryFailed(_)) =>
            throw TestException("recovery call back failure")
        }))
      pa ! "one"
      probe.expectMessage("starting")
      probe.expectMessage("persisting")
      probe.expectMessage("one")
      probe.expectMessage("persisted")
    }

    "signal RecoveryFailure when event handler throws during replay" in {
      val probe = TestProbe[String]()
      val excProbe = TestProbe[Throwable]()
      val pid = PersistenceId.ofUniqueId("wrong-event-1")
      val ref = spawn(failingPersistentActor(pid, probe.ref))

      ref ! "malicious"
      probe.expectMessage("starting")
      probe.expectMessage("persisting")
      probe.expectMessage("malicious")
      probe.expectMessage("persisted")

      LoggingTestKit.error[JournalFailureException].expect {
        // start again and then the event handler will throw
        spawn(failingPersistentActor(pid, probe.ref,
          {
            case (_, RecoveryFailed(t)) =>
              excProbe.ref ! t
          }))

        excProbe.expectMessageType[TestException].message shouldEqual "wrong event"
        probe.expectMessage("stopped")
      }
    }

    "fail recovery if exception from RecoveryCompleted signal handler" in {
      val probe = TestProbe[String]()
      LoggingTestKit.error[JournalFailureException].expect {
        spawn(
          Behaviors
            .supervise(failingPersistentActor(
              PersistenceId.ofUniqueId("recovery-ok"),
              probe.ref,
              {
                case (_, RecoveryCompleted) =>
                  probe.ref.tell("starting")
                  throw TestException("recovery call back failure")
              }))
            // since recovery fails restart supervision is not supposed to be used
            .onFailure(SupervisorStrategy.restart))
        probe.expectMessage("starting")
        probe.expectMessage("stopped")
      }
    }

    "restart with backoff" in {
      val probe = TestProbe[String]()
      val behav = failingPersistentActor(PersistenceId.ofUniqueId("fail-first-2"), probe.ref).onPersistFailure(
        SupervisorStrategy.restartWithBackoff(1.milli, 10.millis, 0.1).withLoggingEnabled(enabled = false))
      val c = spawn(behav)
      probe.expectMessage("starting")
      // fail
      c ! "one"
      probe.expectMessage("persisting")
      probe.expectMessage("one")
      probe.expectMessage("restarting")
      probe.expectMessage("starting")
      // fail
      c ! "two"
      probe.expectMessage("persisting")
      probe.expectMessage("two")
      probe.expectMessage("restarting")
      probe.expectMessage("starting")
      // work!
      c ! "three"
      probe.expectMessage("persisting")
      probe.expectMessage("three")
      probe.expectMessage("persisted")
      // no restart
      probe.expectNoMessage()
    }

    "restart with backoff for recovery" in {
      val probe = TestProbe[String]()
      val behav = failingPersistentActor(PersistenceId.ofUniqueId("fail-recovery-once"), probe.ref).onPersistFailure(
        SupervisorStrategy.restartWithBackoff(1.milli, 10.millis, 0.1).withLoggingEnabled(enabled = false))
      spawn(behav)
      // First time fails, second time should work and call onRecoveryComplete
      probe.expectMessage("restarting")
      probe.expectMessage("starting")
      probe.expectNoMessage()
    }

    "handles rejections" in {
      val probe = TestProbe[String]()
      val behav =
        Behaviors
          .supervise(failingPersistentActor(PersistenceId.ofUniqueId("reject-first"), probe.ref))
          .onFailure[EventRejectedException](
            SupervisorStrategy.restartWithBackoff(1.milli, 5.millis, 0.1).withLoggingEnabled(enabled = false))
      val c = spawn(behav)
      // First time fails, second time should work and call onRecoveryComplete
      probe.expectMessage("starting")
      c ! "one"
      probe.expectMessage("persisting")
      probe.expectMessage("one")
      probe.expectMessage("restarting")
      probe.expectMessage("starting")
      c ! "two"
      probe.expectMessage("persisting")
      probe.expectMessage("two")
      probe.expectMessage("persisted")
      // no restart
      probe.expectNoMessage()
    }

    "stop (default supervisor strategy) if command handler throws" in {
      LoggingTestKit.error[TestException].expect {
        val probe = TestProbe[String]()
        val behav = failingPersistentActor(PersistenceId.ofUniqueId("wrong-command-1"), probe.ref)
        val c = spawn(behav)
        probe.expectMessage("starting")
        c ! "wrong"
        probe.expectMessage("stopped")
      }
    }

    "restart supervisor strategy if command handler throws" in {
      LoggingTestKit.error[TestException].expect {
        val probe = TestProbe[String]()
        val behav = Behaviors
          .supervise(failingPersistentActor(PersistenceId.ofUniqueId("wrong-command-2"), probe.ref))
          .onFailure[TestException](SupervisorStrategy.restart)
        val c = spawn(behav)
        probe.expectMessage("starting")
        c ! "wrong"
        probe.expectMessage("restarting")
      }
    }

    "stop (default supervisor strategy) if side effect callback throws" in {
      LoggingTestKit.error[TestException].expect {
        val probe = TestProbe[String]()
        val behav = failingPersistentActor(PersistenceId.ofUniqueId("wrong-command-3"), probe.ref)
        val c = spawn(behav)
        probe.expectMessage("starting")
        c ! "wrong-callback"
        probe.expectMessage("persisting")
        probe.expectMessage("wrong-callback") // from event handler
        probe.expectMessage("persisted")
        probe.expectMessage("stopped")
      }
    }

    "stop (default supervisor strategy) if signal handler throws" in {
      case object SomeSignal extends Signal
      LoggingTestKit.error[TestException].expect {
        val probe = TestProbe[String]()
        val behav = failingPersistentActor(PersistenceId.ofUniqueId("wrong-signal-handler"), probe.ref,
          {
            case (_, SomeSignal) => throw TestException("from signal")
          })
        val c = spawn(behav)
        probe.expectMessage("starting")
        c.toClassic ! SomeSignal
        probe.expectMessage("stopped")
      }
    }

    "not accept wrong event, before persisting it" in {
      LoggingTestKit.error[TestException].expect {
        val probe = TestProbe[String]()
        val behav = failingPersistentActor(PersistenceId.ofUniqueId("wrong-event-2"), probe.ref)
        val c = spawn(behav)
        probe.expectMessage("starting")
        // event handler will throw for this event
        c ! "wrong-event"
        probe.expectMessage("persisting")
        // but not "persisted"
        probe.expectMessage("stopped")
      }
    }
  }
}
