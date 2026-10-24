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

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.typed.PersistenceId
import pekko.serialization.jackson.CborSerializable

import org.scalatest.wordspec.AnyWordSpecLike

object EventSourcedBehaviorReplySpec {

  sealed trait Command[ReplyMessage] extends CborSerializable
  final case class IncrementWithConfirmation(replyTo: ActorRef[Done]) extends Command[Done]
  final case class IncrementReplyLater(replyTo: ActorRef[Done]) extends Command[Done]
  final case class ReplyNow(replyTo: ActorRef[Done]) extends Command[Done]
  final case class GetValue(replyTo: ActorRef[State]) extends Command[State]

  sealed trait Event extends CborSerializable
  final case class Incremented(delta: Int) extends Event

  final case class State(value: Int, history: Vector[Int]) extends CborSerializable

  def counter(persistenceId: PersistenceId): Behavior[Command[?]] =
    Behaviors.setup(ctx => counter(ctx, persistenceId))

  def counter(
      ctx: ActorContext[Command[?]],
      persistenceId: PersistenceId): EventSourcedBehavior[Command[?], Event, State] = {
    EventSourcedBehavior.withEnforcedReplies[Command[?], Event, State](
      persistenceId,
      emptyState = State(0, Vector.empty),
      commandHandler = (state, command) =>
        command match {

          case IncrementWithConfirmation(replyTo) =>
            Effect.persist(Incremented(1)).thenReply(replyTo)(_ => Done)

          case IncrementReplyLater(replyTo) =>
            Effect.persist(Incremented(1)).thenRun((_: State) => ctx.self ! ReplyNow(replyTo)).thenNoReply()

          case ReplyNow(replyTo) =>
            Effect.reply(replyTo)(Done)

          case GetValue(replyTo) =>
            Effect.reply(replyTo)(state)

        },
      eventHandler = (state, evt) =>
        evt match {
          case Incremented(delta) =>
            State(state.value + delta, state.history :+ state.value)
        })
  }
}

class EventSourcedBehaviorReplySpec
    extends ScalaTestWithActorTestKit(PersistenceTestKitPlugin.config)
    with AnyWordSpecLike
    with LogCapturing {

  import EventSourcedBehaviorReplySpec._

  val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()}")

  private def unstash[Self <: ReplyEffectOps[Int, Int, Self]](effect: ReplyEffectOps[Int, Int, Self]): Self =
    effect.thenUnstashAll()

  private def stoppingBehavior(effect: (Int, ActorRef[Int]) => ReplyEffectWithStop[Int, Int]): Behavior[ActorRef[Int]] =
    EventSourcedBehavior.withEnforcedReplies[ActorRef[Int], Int, Int](
      nextPid(),
      emptyState = 0,
      commandHandler = (state, replyTo) => effect(state, replyTo).thenStop(),
      eventHandler = (state, event) => state + event)

  "A typed persistent actor with commands that are expecting replies" must {

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
      val actor = spawn(stoppingBehavior((_, replyTo) => Effect.reply[Int, Int, Int](replyTo)(42).thenUnstashAll()))
      actor ! probe.ref
      probe.expectMessage(42)
      probe.expectTerminated(actor)
    }

    "retain the stop capability through a generic unstash helper" in {
      val probe = TestProbe[Int]()
      val actor = spawn(stoppingBehavior((_, replyTo) => unstash(Effect.reply[Int, Int, Int](replyTo)(42))))
      actor ! probe.ref
      probe.expectMessage(42)
      probe.expectTerminated(actor)
    }

    "reject stopping a stashed command in both DSLs" in {
      "Effect.stash[Int, Int]().thenUnstashAll()" should compile
      "new org.apache.pekko.persistence.typed.javadsl.EffectFactories[Int, Int]().stash().thenUnstashAll()" should
      compile
      "Effect.stash[Int, Int]().thenStop()" shouldNot compile
      "Effect.stash[Int, Int]().thenUnstashAll().thenStop()" shouldNot compile
      "new org.apache.pekko.persistence.typed.javadsl.EffectFactories[Int, Int]().stash().thenStop()" shouldNot compile
      "new org.apache.pekko.persistence.typed.javadsl.EffectFactories[Int, Int]().stash().thenUnstashAll().thenStop()" shouldNot
      compile
    }

    "require helpers to retain the stop capability explicitly" in {
      "{ val effect: ReplyEffect[Int, Int] = Effect.noReply[Int, Int]; effect.thenStop() }" shouldNot compile
    }

    "stop after explicitly choosing not to reply" in {
      val probe = TestProbe[Int]()
      val actor = spawn(stoppingBehavior((_, _) => Effect.noReply))
      actor ! probe.ref
      probe.expectTerminated(actor)
    }

    "persist an event thenReply" in {
      val c = spawn(counter(nextPid()))
      val probe = TestProbe[Done]()
      c ! IncrementWithConfirmation(probe.ref)
      probe.expectMessage(Done)

      c ! IncrementWithConfirmation(probe.ref)
      c ! IncrementWithConfirmation(probe.ref)
      probe.expectMessage(Done)
      probe.expectMessage(Done)
    }

    "persist an event thenReply later" in {
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
      queryProbe.expectMessage(State(1, Vector(0)))
    }
  }
}
