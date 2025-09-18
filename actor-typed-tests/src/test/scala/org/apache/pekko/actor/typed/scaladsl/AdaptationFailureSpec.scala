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

package org.apache.pekko.actor.typed.scaladsl

import scala.concurrent.Promise

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.TestException
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.Behavior
import pekko.actor.typed.MessageAdaptionFailure
import pekko.actor.typed.PreRestart
import pekko.actor.typed.Signal
import pekko.actor.typed.Terminated

import org.scalatest.wordspec.AnyWordSpecLike

object AdaptationFailureSpec {

  def emptyAbstractBehavior: Behavior[Any] = Behaviors.setup(new EmptyAbstractBehavior(_))
  class EmptyAbstractBehavior(ctx: ActorContext[Any]) extends AbstractBehavior[Any](ctx) {
    def onMessage(msg: Any): Behavior[Any] = this
  }

  def abstractBehaviorHandlingOtherSignals: Behavior[Any] = Behaviors.setup(new AbstractBehaviorHandlingOtherSignals(_))
  class AbstractBehaviorHandlingOtherSignals(ctx: ActorContext[Any]) extends AbstractBehavior[Any](ctx) {

    def onMessage(msg: Any): Behavior[Any] = this

    override def onSignal: PartialFunction[Signal, Behavior[Any]] = {
      case PreRestart => Behaviors.same
    }
  }

  def abstractBehaviorHandlingMessageAdaptionFailure: Behavior[Any] =
    Behaviors.setup(new AbstractBehaviorHandlingMessageAdaptionFailure(_))
  class AbstractBehaviorHandlingMessageAdaptionFailure(ctx: ActorContext[Any]) extends AbstractBehavior[Any](ctx) {

    def onMessage(msg: Any): Behavior[Any] = this

    override def onSignal: PartialFunction[Signal, Behavior[Any]] = {
      case MessageAdaptionFailure(_) => Behaviors.same
    }
  }

}
class AdaptationFailureSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  import AdaptationFailureSpec._

  val crashingBehaviors: List[(String, Behavior[Any])] =
    "receive" -> Behaviors.receive[Any]((_, _) => Behaviors.same) ::
    "receiveMessage" -> Behaviors.receiveMessage[Any](_ => Behaviors.same) ::
    "receivePartial" -> Behaviors.receivePartial[Any](PartialFunction.empty) ::
    "receiveSignal" -> Behaviors.receiveSignal[Any](PartialFunction.empty) ::
    "receiveSignal not catching adaption failure" ->
    Behaviors.receiveSignal[Any] {
      case (_, PreRestart) => Behaviors.same
    } ::
    "AbstractBehavior" -> emptyAbstractBehavior ::
    "AbstractBehavior handling other signals" -> abstractBehaviorHandlingOtherSignals ::
    Nil

  val nonCrashingBehaviors: List[(String, Behavior[Any])] =
    "empty" -> Behaviors.empty[Any] ::
    "ignore" -> Behaviors.ignore[Any] ::
    "receiveSignal catching adaption failure" ->
    Behaviors.receiveSignal[Any] {
      case (_, MessageAdaptionFailure(_)) => Behaviors.same
    } ::
    "AbstractBehavior handling MessageAdaptionFailure" -> abstractBehaviorHandlingMessageAdaptionFailure ::
    Nil

  "Failure in an adapter" must {

    crashingBehaviors.foreach {
      case (name, behavior) =>
        s"default to crash the actor or $name" in {
          val probe = createTestProbe()
          val ref = spawn(Behaviors.setup[Any] { ctx =>
            val adapter = ctx.messageAdapter[Any](_ => throw TestException("boom"))
            adapter ! "go boom"

            behavior
          })
          probe.expectTerminated(ref)
        }
    }

    nonCrashingBehaviors.foreach {
      case (name, behavior) =>
        s"ignore the failure for $name" in {
          val probe = createTestProbe[Any]()
          val threw = Promise[Done]()
          val ref = spawn(Behaviors.setup[Any] { ctx =>
            val adapter = ctx.messageAdapter[Any] { _ =>
              threw.success(Done)
              throw TestException("boom")
            }
            adapter ! "go boom"
            behavior
          })
          spawn(Behaviors.setup[Any] { ctx =>
            ctx.watch(ref)

            Behaviors.receiveSignal {
              case (_, Terminated(`ref`)) =>
                probe.ref ! "actor-stopped"
                Behaviors.same
            }
          })

          probe.expectNoMessage()
        }
    }

  }
}
