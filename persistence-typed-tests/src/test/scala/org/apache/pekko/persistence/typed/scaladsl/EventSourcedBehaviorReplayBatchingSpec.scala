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

import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import pekko.actor.typed.{ ActorRef, Behavior, PreRestart, SupervisorStrategy }
import pekko.persistence.JournalProtocol.{ ReplayBatchResponse, ReplayedMessage }
import pekko.persistence.PersistentRepr
import pekko.persistence.journal.SteppingInmemJournal
import pekko.persistence.journal.inmem.InmemJournal
import pekko.persistence.typed.{ PersistenceId, RecoveryCompleted, RecoveryFailed }
import pekko.persistence.typed.internal.EventSourcedBehaviorImpl.WriterIdentity

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object DelayedReplayInmemJournal {
  private var plannedInvocations = 0
  private var nextInvocation = 0
  private var started = Vector.empty[Promise[Unit]]
  private var completions = Map.empty[Int, () => Unit]

  def prepare(invocations: Int): Vector[Future[Unit]] = synchronized {
    require(nextInvocation == plannedInvocations && completions.isEmpty, "previous delayed replay is still active")
    plannedInvocations = invocations
    nextInvocation = 0
    started = Vector.fill(invocations)(Promise[Unit]())
    started.map(_.future)
  }

  private def claim(): Option[Int] = synchronized {
    if (nextInvocation == plannedInvocations) None
    else {
      val invocation = nextInvocation
      nextInvocation += 1
      Some(invocation)
    }
  }

  private def register(invocation: Int, completion: () => Unit): Unit = synchronized {
    completions = completions.updated(invocation, completion)
    started(invocation).success(())
  }

  def complete(invocation: Int): Unit = {
    val completion = synchronized {
      val result = completions.getOrElse(invocation, throw new IllegalStateException("delayed replay has not started"))
      completions -= invocation
      result
    }
    completion()
  }
}

final class DelayedReplayInmemJournal extends InmemJournal {
  import DelayedReplayInmemJournal._
  import context.dispatcher

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
      recoveryCallback: PersistentRepr => Unit): Future[Unit] =
    claim() match {
      case None             => super.asyncReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max)(recoveryCallback)
      case Some(invocation) =>
        val buffered = Vector.newBuilder[PersistentRepr]
        super
          .asyncReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max)(buffered += _)
          .flatMap { _ =>
            val promise = Promise[Unit]()
            val replayed = buffered.result()
            register(
              invocation,
              () => {
                replayed.foreach(recoveryCallback)
                promise.success(())
              })
            promise.future
          }
    }
}

object EventSourcedBehaviorReplayBatchingSpec {
  val JournalId = "event-sourced-behavior-replay-batching-spec"

  sealed trait Command
  final case class PersistAll(events: Vector[String]) extends Command
  final case class Applied(event: String)
  final case class RecoveryFinished(state: Vector[String])
  case object RecoveryFailedObserved
  case object Restarting

  val config =
    SteppingInmemJournal
      .config(JournalId)
      .withFallback(ConfigFactory.parseString(s"""
          pekko.persistence.journal.stepping-inmem {
            replay-batch-size = 2
            replay-filter.mode = off
          }
          delayed-replay-journal = $${pekko.persistence.journal.inmem}
          delayed-replay-journal {
            class = "org.apache.pekko.persistence.typed.scaladsl.DelayedReplayInmemJournal"
            replay-batch-size = 2
            replay-filter.mode = off
            recovery-event-timeout = 1s
          }
        """)).withFallback(ConfigFactory.defaultReference()).resolve()

  def behavior(persistenceId: PersistenceId, probe: ActorRef[AnyRef]): Behavior[Command] =
    EventSourcedBehavior[Command, String, Vector[String]](
      persistenceId,
      emptyState = Vector.empty,
      commandHandler = (_, command) =>
        command match {
          case PersistAll(events) => Effect.persist(events)
        },
      eventHandler = (state, event) => {
        probe ! Applied(event)
        state :+ event
      }).receiveSignal {
      case (state, RecoveryCompleted) => probe ! RecoveryFinished(state)
    }

  def restartingBehavior(persistenceId: PersistenceId, probe: ActorRef[AnyRef]): Behavior[Command] =
    EventSourcedBehavior[Command, String, Vector[String]](
      persistenceId,
      emptyState = Vector.empty,
      commandHandler = (_, command) =>
        command match {
          case PersistAll(events) => Effect.persist(events)
        },
      eventHandler = (state, event) => {
        probe ! Applied(event)
        state :+ event
      }).withJournalPluginId("delayed-replay-journal").receiveSignal {
      case (state, RecoveryCompleted) => probe ! RecoveryFinished(state)
      case (_, RecoveryFailed(_))     => probe ! RecoveryFailedObserved
      case (_, PreRestart)            => probe ! Restarting
    }.onPersistFailure(
      SupervisorStrategy.restartWithBackoff(10.millis, 10.millis, randomFactor = 0.0).withLoggingEnabled(false))
}

class EventSourcedBehaviorReplayBatchingSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorReplayBatchingSpec.config)
    with AnyWordSpecLike
    with LogCapturing {
  import EventSourcedBehaviorReplayBatchingSpec._

  import org.apache.pekko.actor.typed.scaladsl.adapter._
  private implicit val classicSystem: pekko.actor.ActorSystem = system.toClassic

  "An EventSourcedBehavior recovery" must {
    "acknowledge each bounded replay batch before the journal produces the next batch" in {
      val probe = createTestProbe[AnyRef]()
      val persistenceId = PersistenceId.ofUniqueId("bounded-replay")
      val events = Vector("a", "b", "c", "d", "e")

      val persisting = spawn(behavior(persistenceId, probe.ref))
      probe.awaitAssert(SteppingInmemJournal.getRef(JournalId), 3.seconds)
      val journal = SteppingInmemJournal.getRef(JournalId)

      SteppingInmemJournal.step(journal)
      probe.expectMessage(RecoveryFinished(Vector.empty))

      persisting ! PersistAll(events)
      SteppingInmemJournal.step(journal)
      events.foreach(event => probe.expectMessage(Applied(event)))

      testKit.stop(persisting)
      probe.expectTerminated(persisting)

      spawn(behavior(persistenceId, probe.ref))
      SteppingInmemJournal.step(journal) // read highest sequence number

      SteppingInmemJournal.step(journal) // replay 1-2
      probe.expectMessage(Applied("a"))
      probe.expectMessage(Applied("b"))
      probe.expectNoMessage(100.millis)

      SteppingInmemJournal.step(journal) // replay 3-4
      probe.expectMessage(Applied("c"))
      probe.expectMessage(Applied("d"))
      probe.expectNoMessage(100.millis)

      SteppingInmemJournal.step(journal) // replay 5 and complete
      probe.expectMessage(Applied("e"))
      probe.expectMessage(RecoveryFinished(events))
    }

    "isolate a timed-out replay from a new recovery using the same actor reference" in {
      val probe = createTestProbe[AnyRef]()
      val persistenceId = PersistenceId.ofUniqueId("timed-out-replay")
      val events = Vector("a", "b", "c")

      val persisting = spawn(restartingBehavior(persistenceId, probe.ref))
      probe.expectMessage(RecoveryFinished(Vector.empty))
      persisting ! PersistAll(events)
      events.foreach(event => probe.expectMessage(Applied(event)))
      testKit.stop(persisting)
      probe.expectTerminated(persisting)

      val replayStarted = DelayedReplayInmemJournal.prepare(invocations = 2)
      val recovering = spawn(restartingBehavior(persistenceId, probe.ref))
      Await.result(replayStarted.head, 3.seconds)
      val timedOutActorInstanceId = WriterIdentity.instanceIdCounter.get() - 1
      probe.expectMessageType[RecoveryFailedObserved.type](5.seconds)
      probe.expectMessage(Restarting)
      Await.result(replayStarted(1), 3.seconds)

      recovering.toClassic.tell(
        ReplayBatchResponse(
          timedOutActorInstanceId,
          ReplayedMessage(PersistentRepr("stale", sequenceNr = 1L, persistenceId = persistenceId.id))),
        Actor.noSender)
      DelayedReplayInmemJournal.complete(0)
      probe.expectNoMessage(100.millis)

      DelayedReplayInmemJournal.complete(1)
      events.foreach(event => probe.expectMessage(Applied(event)))
      probe.expectMessage(RecoveryFinished(events))
    }
  }
}
