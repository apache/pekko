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

package org.apache.pekko.persistence.typed.scaladsl

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import org.apache.pekko
import pekko.actor.testkit.typed.TestException
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.AtomicWrite
import pekko.persistence.journal.inmem.InmemJournal
import pekko.persistence.typed.JournalPersistFailed
import pekko.persistence.typed.JournalPersistRejected
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.RecoveryCompleted
import pekko.serialization.jackson.CborSerializable

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

// Custom journal that checks event flags to determine whether to reject or fail writes
class SignalTestJournal extends InmemJournal {
  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    // Check if any of the events have the shouldReject or shouldFail flag set
    val shouldReject = messages.exists { atomicWrite =>
      atomicWrite.payload.exists { persistentRepr =>
        persistentRepr.payload match {
          case event: EventSourcedBehaviorSignalSpec.Incremented => event.shouldReject
          case _                                                 => false
        }
      }
    }

    val shouldFail = messages.exists { atomicWrite =>
      atomicWrite.payload.exists { persistentRepr =>
        persistentRepr.payload match {
          case event: EventSourcedBehaviorSignalSpec.Incremented => event.shouldFail
          case _                                                 => false
        }
      }
    }

    if (shouldReject) {
      // Return a successful future with a failed Try to simulate rejection
      Future.successful(messages.map(_ =>
        Try { throw TestException("Journal rejected the event") }
      ))
    } else if (shouldFail) {
      // Return a failed future to simulate journal failure
      Future.failed(TestException("Journal failed to persist the event"))
    } else {
      super.asyncWriteMessages(messages)
    }
  }
}

object EventSourcedBehaviorSignalSpec {
  // Commands
  sealed trait Command extends CborSerializable
  case object Increment extends Command
  case object IncrementWithReject extends Command
  case object IncrementWithFailure extends Command

  // Events
  sealed trait Event extends CborSerializable
  final case class Incremented(delta: Int, shouldReject: Boolean = false, shouldFail: Boolean = false) extends Event

  // State
  final case class State(value: Int) extends CborSerializable

  // Configuration for tests
  val config: Config = ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    pekko.persistence.journal.plugin = "signal-test-journal"
    signal-test-journal = $${pekko.persistence.journal.inmem}
    signal-test-journal {
      class = "${classOf[SignalTestJournal].getName}"
    }
    """).withFallback(ConfigFactory.defaultReference()).resolve()

  // Create a behavior that tracks signals
  def signalTrackingBehavior(
      persistenceId: PersistenceId,
      signalProbe: ActorRef[String]): Behavior[Command] = {

    // Create the base EventSourcedBehavior
    val eventSourcedBehavior = EventSourcedBehavior[Command, Event, State](
      persistenceId,
      emptyState = State(0),
      commandHandler = (_, cmd) =>
        cmd match {
          case Increment =>
            Effect.persist(Incremented(1))
          case IncrementWithReject =>
            // Create an event that signals it should be rejected
            Effect.persist(Incremented(1, shouldReject = true))
          case IncrementWithFailure =>
            // Create an event that signals it should fail
            Effect.persist(Incremented(1, shouldFail = true))
        },
      eventHandler = (state, evt) =>
        evt match {
          case Incremented(delta, _, _) =>
            State(state.value + delta)
        })
      .receiveSignal {
        case (_, RecoveryCompleted) =>
          signalProbe ! "RecoveryCompleted"
        case (_, signal: JournalPersistRejected) =>
          val message = s"JournalPersistRejected: ${signal.failure.getMessage}"
          signalProbe ! message
        case (_, signal: JournalPersistFailed) =>
          val message = s"JournalPersistFailed: ${signal.failure.getMessage}"
          signalProbe ! message
      }

    // We don't need to handle failures with supervision since we're only testing the signals
    eventSourcedBehavior
  }
}

class EventSourcedBehaviorSignalSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorSignalSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  import EventSourcedBehaviorSignalSpec._

  private val pidCounter = new java.util.concurrent.atomic.AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"signal-test-${pidCounter.incrementAndGet()}")

  "An EventSourcedBehavior" must {
    "receive JournalPersistRejected signal when journal rejects events" in {
      // Create a probe to track signals
      val signalProbe = createTestProbe[String]()

      // Create a behavior that will track signals
      val behavior = signalTrackingBehavior(nextPid(), signalProbe.ref)

      // Spawn the actor
      val actor = spawn(behavior)

      // Wait for recovery to complete
      signalProbe.expectMessage("RecoveryCompleted")

      // Send a command that will trigger a rejection
      actor ! IncrementWithReject

      // Verify that the JournalPersistRejected signal was received
      signalProbe.expectMessage(5.seconds, "JournalPersistRejected: Journal rejected the event")
    }

    "receive JournalPersistFailed signal when journal fails to persist events" in {
      // Create a probe to track signals
      val signalProbe = createTestProbe[String]()

      // Create a behavior that will track signals
      val behavior = signalTrackingBehavior(nextPid(), signalProbe.ref)

      // Spawn the actor
      val actor = spawn(behavior)

      // Wait for recovery to complete
      signalProbe.expectMessage("RecoveryCompleted")

      // Send a command that will trigger a failure
      actor ! IncrementWithFailure

      // Verify that the JournalPersistFailed signal was received
      signalProbe.expectMessage(5.seconds, "JournalPersistFailed: Journal failed to persist the event")
    }

    "receive no signal when journal doesn't fail" in {
      // Create a probe to track signals
      val signalProbe = createTestProbe[String]()

      // Create a behavior that will track signals
      val behavior = signalTrackingBehavior(nextPid(), signalProbe.ref)

      // Spawn the actor
      val actor = spawn(behavior)

      // Wait for recovery to complete
      signalProbe.expectMessage("RecoveryCompleted")

      // Send a command that won't trigger a failure
      actor ! Increment

      // Verify that no signal was emitted
      signalProbe.expectNoMessage(5.seconds)
    }
  }
}
