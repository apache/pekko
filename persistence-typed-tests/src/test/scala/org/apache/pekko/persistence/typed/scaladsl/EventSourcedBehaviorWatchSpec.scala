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

import org.apache.pekko
import pekko.actor.testkit.typed.TestException
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.LoggingTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed._
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.{ Recovery => ClassicRecovery }
import pekko.persistence.typed.NoOpEventAdapter
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.RecoveryCompleted
import pekko.persistence.typed.internal.BehaviorSetup
import pekko.persistence.typed.internal.EventSourcedBehaviorImpl.WriterIdentity
import pekko.persistence.typed.internal.EventSourcedSettings
import pekko.persistence.typed.internal.InternalProtocol
import pekko.persistence.typed.internal.NoOpSnapshotAdapter
import pekko.persistence.typed.internal.StashState
import pekko.serialization.jackson.CborSerializable
import pekko.util.ConstantFun

import org.scalatest.wordspec.AnyWordSpecLike

import org.slf4j.LoggerFactory

object EventSourcedBehaviorWatchSpec {
  sealed trait Command extends CborSerializable
  case object Fail extends Command
  case object Stop extends Command
  final case class ChildHasFailed(t: pekko.actor.typed.ChildFailed)
  final case class HasTerminated(ref: ActorRef[_])
}

class EventSourcedBehaviorWatchSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  import EventSourcedBehaviorWatchSpec._

  private val cause = TestException("Dodge this.")

  private val pidCounter = new AtomicInteger(0)

  private def nextPid: PersistenceId = PersistenceId.ofUniqueId(s"${pidCounter.incrementAndGet()}")

  private val logger = LoggerFactory.getLogger(this.getClass)

  private def setup(
      pf: PartialFunction[(String, Signal), Unit],
      settings: EventSourcedSettings,
      context: ActorContext[_]): BehaviorSetup[Command, String, String] =
    new BehaviorSetup[Command, String, String](
      context.asInstanceOf[ActorContext[InternalProtocol]],
      nextPid,
      emptyState = "",
      commandHandler = (_, _) => Effect.none,
      eventHandler = (state, evt) => state + evt,
      WriterIdentity.newIdentity(),
      pf,
      _ => Set.empty[String],
      NoOpEventAdapter.instance[String],
      NoOpSnapshotAdapter.instance[String],
      snapshotWhen = ConstantFun.scalaAnyThreeToFalse,
      ClassicRecovery(),
      RetentionCriteria.disabled,
      holdingRecoveryPermit = false,
      settings = settings,
      stashState = new StashState(context.asInstanceOf[ActorContext[InternalProtocol]], settings),
      replication = None,
      publishEvents = false,
      internalLoggerFactory = () => logger)

  "A typed persistent parent actor watching a child" must {

    "throw a DeathPactException from parent when not handling the child Terminated signal" in {

      val parent =
        spawn(Behaviors.setup[Command] { context =>
          val child = context.spawnAnonymous(Behaviors.receive[Command] { (_, _) =>
            throw cause
          })

          context.watch(child)

          EventSourcedBehavior[Command, String, String](nextPid, emptyState = "",
            commandHandler = (_, cmd) => {
              child ! cmd
              Effect.none
            }, eventHandler = (state, evt) => state + evt)
        })

      LoggingTestKit.error[TestException].expect {
        LoggingTestKit.error[DeathPactException].expect {
          parent ! Fail
        }
      }
      createTestProbe().expectTerminated(parent)
    }

    "behave as expected if a user's signal handler is side effecting" in {
      val signalHandler: PartialFunction[(String, Signal), Unit] = {
        case (_, RecoveryCompleted) =>
          java.time.Instant.now.getNano
          Behaviors.same
      }

      Behaviors.setup[Command] { context =>
        val settings = EventSourcedSettings(context.system, "", "")

        setup(signalHandler, settings, context).onSignal("", RecoveryCompleted, false) shouldEqual true
        setup(PartialFunction.empty, settings, context).onSignal("", RecoveryCompleted, false) shouldEqual false

        Behaviors.empty
      }

      val parent =
        spawn(Behaviors.setup[Command] { context =>
          val child = context.spawnAnonymous(Behaviors.receive[Command] { (_, _) =>
            throw cause
          })

          context.watch(child)

          EventSourcedBehavior[Command, String, String](nextPid, emptyState = "",
            commandHandler = (_, cmd) => {
              child ! cmd
              Effect.none
            }, eventHandler = (state, evt) => state + evt).receiveSignal(signalHandler)
        })

      LoggingTestKit.error[TestException].expect {
        LoggingTestKit.error[DeathPactException].expect {
          parent ! Fail
        }
      }
      createTestProbe().expectTerminated(parent)
    }

    "receive a Terminated when handling the signal" in {
      val probe = TestProbe[AnyRef]()

      val parent =
        spawn(Behaviors.setup[Stop.type] { context =>
          val child = context.spawnAnonymous(Behaviors.setup[Stop.type] { c =>
            Behaviors.receive[Stop.type] { (_, _) =>
              context.stop(c.self)
              Behaviors.stopped
            }
          })

          probe.ref ! child
          context.watch(child)

          EventSourcedBehavior[Stop.type, String, String](nextPid, emptyState = "",
            commandHandler = (_, cmd) => {
              child ! cmd
              Effect.none
            }, eventHandler = (state, evt) => state + evt).receiveSignal {
            case (_, t: Terminated) =>
              probe.ref ! HasTerminated(t.ref)
              Behaviors.stopped
          }
        })

      val child = probe.expectMessageType[ActorRef[Stop.type]]

      parent ! Stop
      probe.expectMessageType[HasTerminated].ref shouldEqual child
    }

    "receive a ChildFailed when handling the signal" in {
      val probe = TestProbe[AnyRef]()

      val parent =
        spawn(Behaviors.setup[Fail.type] { context =>
          val child = context.spawnAnonymous(Behaviors.receive[Fail.type] { (_, _) =>
            throw cause
          })

          probe.ref ! child
          context.watch(child)

          EventSourcedBehavior[Fail.type, String, String](nextPid, emptyState = "",
            commandHandler = (_, cmd) => {
              child ! cmd
              Effect.none
            }, eventHandler = (state, evt) => state + evt).receiveSignal {
            case (_, t: ChildFailed) =>
              probe.ref ! ChildHasFailed(t)
              Behaviors.same
          }
        })

      val child = probe.expectMessageType[ActorRef[Fail.type]]

      LoggingTestKit.error[TestException].expect {
        parent ! Fail
      }
      val failed = probe.expectMessageType[ChildHasFailed].t
      failed.ref shouldEqual child
      failed.cause shouldEqual cause
    }

  }
}
