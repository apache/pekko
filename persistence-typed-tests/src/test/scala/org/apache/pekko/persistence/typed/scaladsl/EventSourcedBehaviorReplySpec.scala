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

import java.util.concurrent.atomic.AtomicInteger

object EventSourcedBehaviorReplySpec {

  sealed trait Command[ReplyMessage] extends CborSerializable
  final case class IncrementWithConfirmation(replyTo: ActorRef[Done]) extends Command[Done]
  final case class IncrementReplyLater(replyTo: ActorRef[Done]) extends Command[Done]
  final case class ReplyNow(replyTo: ActorRef[Done]) extends Command[Done]
  final case class GetValue(replyTo: ActorRef[State]) extends Command[State]

  sealed trait Event extends CborSerializable
  final case class Incremented(delta: Int) extends Event

  final case class State(value: Int, history: Vector[Int]) extends CborSerializable

  def counter(persistenceId: PersistenceId): Behavior[Command[_]] =
    Behaviors.setup(ctx => counter(ctx, persistenceId))

  def counter(
      ctx: ActorContext[Command[_]],
      persistenceId: PersistenceId): EventSourcedBehavior[Command[_], Event, State] =
    EventSourcedBehavior.withEnforcedReplies[Command[_], Event, State](
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

class EventSourcedBehaviorReplySpec
    extends ScalaTestWithActorTestKit(PersistenceTestKitPlugin.config)
    with AnyWordSpecLike
    with LogCapturing {

  import EventSourcedBehaviorReplySpec._

  val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()})")

  "A typed persistent actor with commands that are expecting replies" must {

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
