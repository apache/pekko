/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin
import pekko.persistence.typed.PersistenceId
import pekko.serialization.jackson.CborSerializable

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object DurableStateBehaviorReplySpec {
  def conf: Config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    """))

  sealed trait Command[ReplyMessage] extends CborSerializable
  final case class IncrementWithConfirmation(replyTo: ActorRef[Done]) extends Command[Done]
  final case class IncrementReplyLater(replyTo: ActorRef[Done]) extends Command[Done]
  final case class ReplyNow(replyTo: ActorRef[Done]) extends Command[Done]
  final case class GetValue(replyTo: ActorRef[State]) extends Command[State]
  final case class DeleteWithConfirmation(replyTo: ActorRef[Done]) extends Command[Done]
  case object Increment extends Command[Nothing]
  case class IncrementBy(by: Int) extends Command[Nothing]

  final case class State(value: Int) extends CborSerializable

  def counter(persistenceId: PersistenceId): Behavior[Command[?]] =
    Behaviors.setup(ctx => counter(ctx, persistenceId))

  def counter(ctx: ActorContext[Command[?]], persistenceId: PersistenceId): DurableStateBehavior[Command[?], State] = {
    DurableStateBehavior.withEnforcedReplies[Command[?], State](
      persistenceId,
      emptyState = State(0),
      commandHandler = (state, command) =>
        command match {

          case IncrementWithConfirmation(replyTo) =>
            Effect.persist(state.copy(value = state.value + 1)).thenReply(replyTo)(_ => Done)

          case IncrementReplyLater(replyTo) =>
            Effect
              .persist(state.copy(value = state.value + 1))
              .thenRun((_: State) => ctx.self ! ReplyNow(replyTo))
              .thenNoReply()

          case ReplyNow(replyTo) =>
            Effect.reply(replyTo)(Done)

          case GetValue(replyTo) =>
            Effect.reply(replyTo)(state)

          case DeleteWithConfirmation(replyTo) =>
            Effect.delete[State]().thenReply(replyTo)(_ => Done)

          case _ => ???

        })
  }

}

class DurableStateBehaviorReplySpec
    extends ScalaTestWithActorTestKit(DurableStateBehaviorReplySpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  import DurableStateBehaviorReplySpec._

  val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()}")

  private def unstash[Self <: ReplyEffectOps[Int, Self]](effect: ReplyEffectOps[Int, Self]): Self =
    effect.thenUnstashAll()

  private def stoppingBehavior(effect: (Int, ActorRef[Int]) => ReplyEffectWithStop[Int]): Behavior[ActorRef[Int]] =
    DurableStateBehavior.withEnforcedReplies[ActorRef[Int], Int](
      nextPid(),
      emptyState = 0,
      commandHandler = (state, replyTo) => effect(state, replyTo).thenStop())

  "A DurableStateBehavior actor with commands that are expecting replies" must {

    "persist then reply with the new state and stop" in {
      val probe = TestProbe[Int]()
      val actor = spawn(stoppingBehavior((_, replyTo) => Effect.persist(1).thenReply(replyTo)(identity)))
      actor ! probe.ref
      probe.expectMessage(1)
      probe.expectTerminated(actor)
    }

    "reply and stop without persisting" in {
      val probe = TestProbe[Int]()
      val actor = spawn(stoppingBehavior((_, replyTo) => Effect.reply(replyTo)(42)))
      actor ! probe.ref
      probe.expectMessage(42)
      probe.expectTerminated(actor)
    }

    "retain the stop capability after unstashing" in {
      val probe = TestProbe[Int]()
      val actor = spawn(stoppingBehavior((_, replyTo) => Effect.reply[Int, Int](replyTo)(42).thenUnstashAll()))
      actor ! probe.ref
      probe.expectMessage(42)
      probe.expectTerminated(actor)
    }

    "retain the stop capability through a generic unstash helper" in {
      val probe = TestProbe[Int]()
      val actor = spawn(stoppingBehavior((_, replyTo) => unstash(Effect.reply[Int, Int](replyTo)(42))))
      actor ! probe.ref
      probe.expectMessage(42)
      probe.expectTerminated(actor)
    }

    "reject stopping a stashed command in both DSLs" in {
      "Effect.stash[Int]().thenUnstashAll()" should compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().stash().thenUnstashAll()" should
      compile
      "Effect.stash[Int]().thenStop()" shouldNot compile
      "Effect.stash[Int]().thenUnstashAll().thenStop()" shouldNot compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().stash().thenStop()" shouldNot compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().stash().thenUnstashAll().thenStop()" shouldNot
      compile
    }

    "require helpers to retain the stop capability explicitly" in {
      "{ val effect: ReplyEffect[Int] = Effect.noReply[Int]; effect.thenStop() }" shouldNot compile
    }

    "stop after explicitly choosing not to reply" in {
      val probe = TestProbe[Int]()
      val actor = spawn(stoppingBehavior((_, _) => Effect.noReply))
      actor ! probe.ref
      probe.expectTerminated(actor)
    }

    "persist state thenReply" in {
      val c = spawn(counter(nextPid()))
      val probe = TestProbe[Done]()
      c ! IncrementWithConfirmation(probe.ref)
      probe.expectMessage(Done)

      c ! IncrementWithConfirmation(probe.ref)
      c ! IncrementWithConfirmation(probe.ref)
      probe.expectMessage(Done)
      probe.expectMessage(Done)
    }

    "persist state thenReply later" in {
      val c = spawn(counter(nextPid()))
      val probe = TestProbe[Done]()
      c ! IncrementReplyLater(probe.ref)
      probe.expectMessage(Done)
    }

    "reply to query command" in {
      val c = spawn(counter(nextPid()))
      val updateProbe = TestProbe[Done]()
      c ! IncrementWithConfirmation(updateProbe.ref)

      val queryProbe = TestProbe[State]()
      c ! GetValue(queryProbe.ref)
      queryProbe.expectMessage(State(1))
    }

    "delete state thenReply" in {
      val c = spawn(counter(nextPid()))
      val updateProbe = TestProbe[Done]()
      c ! IncrementWithConfirmation(updateProbe.ref)
      updateProbe.expectMessage(Done)

      val deleteProbe = TestProbe[Done]()
      c ! DeleteWithConfirmation(deleteProbe.ref)
      deleteProbe.expectMessage(Done)

      val queryProbe = TestProbe[State]()
      c ! GetValue(queryProbe.ref)
      queryProbe.expectMessage(State(0))
    }

    "handle commands sequentially" in {
      val c = spawn(counter(nextPid()))
      val probe = TestProbe[Any]()

      c ! IncrementWithConfirmation(probe.ref)
      c ! IncrementWithConfirmation(probe.ref)
      c ! IncrementWithConfirmation(probe.ref)
      c ! GetValue(probe.ref)
      probe.expectMessage(Done)
      probe.expectMessage(Done)
      probe.expectMessage(Done)
      probe.expectMessage(State(3))

      c ! IncrementWithConfirmation(probe.ref)
      c ! IncrementWithConfirmation(probe.ref)
      c ! DeleteWithConfirmation(probe.ref)
      c ! GetValue(probe.ref)
      probe.expectMessage(Done)
      probe.expectMessage(Done)
      probe.expectMessage(Done)
      probe.expectMessage(State(0))
    }
  }
}
