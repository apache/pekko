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

package docs.org.apache.pekko.persistence.typed

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.Done
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.typed.state.scaladsl.Effect

//#structure
//#behavior
import org.apache.pekko
import pekko.persistence.typed.state.scaladsl.DurableStateBehavior
import pekko.persistence.typed.PersistenceId

//#behavior
//#structure

import scala.annotation.nowarn
import pekko.serialization.jackson.CborSerializable

// unused variables in pattern match are useful in the docs
@nowarn
object DurableStatePersistentBehaviorCompileOnly {
  object FirstExample {
    // #command
    sealed trait Command[ReplyMessage] extends CborSerializable
    final case object Increment extends Command[Nothing]
    final case class IncrementBy(value: Int) extends Command[Nothing]
    final case class GetValue(replyTo: ActorRef[State]) extends Command[State]
    final case object Delete extends Command[Nothing]
    // #command

    // #state
    final case class State(value: Int) extends CborSerializable
    // #state

    // #command-handler
    import pekko.persistence.typed.state.scaladsl.Effect

    val commandHandler: (State, Command[_]) => Effect[State] = (state, command) =>
      command match {
        case Increment         => Effect.persist(state.copy(value = state.value + 1))
        case IncrementBy(by)   => Effect.persist(state.copy(value = state.value + by))
        case GetValue(replyTo) => Effect.reply(replyTo)(state)
        case Delete            => Effect.delete[State]()
      }
    // #command-handler

    // #behavior
    def counter(id: String): DurableStateBehavior[Command[_], State] = {
      DurableStateBehavior.apply[Command[_], State](
        persistenceId = PersistenceId.ofUniqueId(id),
        emptyState = State(0),
        commandHandler = commandHandler)
    }
    // #behavior
  }

  // #structure
  object MyPersistentCounter {
    sealed trait Command[ReplyMessage] extends CborSerializable

    final case class State(value: Int) extends CborSerializable

    def counter(persistenceId: PersistenceId): DurableStateBehavior[Command[_], State] = {
      DurableStateBehavior.apply[Command[_], State](
        persistenceId,
        emptyState = State(0),
        commandHandler =
          (state, command) => throw new NotImplementedError("TODO: process the command & return an Effect"))
    }
  }
  // #structure

  import MyPersistentCounter._

  object MyPersistentCounterWithReplies {

    // #effects
    sealed trait Command[ReplyMessage] extends CborSerializable
    final case class IncrementWithConfirmation(replyTo: ActorRef[Done]) extends Command[Done]
    final case class GetValue(replyTo: ActorRef[State]) extends Command[State]

    final case class State(value: Int) extends CborSerializable

    def counter(persistenceId: PersistenceId): DurableStateBehavior[Command[_], State] = {
      DurableStateBehavior.withEnforcedReplies[Command[_], State](
        persistenceId,
        emptyState = State(0),
        commandHandler = (state, command) =>
          command match {

            case IncrementWithConfirmation(replyTo) =>
              Effect.persist(state.copy(value = state.value + 1)).thenReply(replyTo)(_ => Done)

            case GetValue(replyTo) =>
              Effect.reply(replyTo)(state)
          })
    }
    // #effects
  }

  object BehaviorWithContext {
    // #actor-context
    import org.apache.pekko
    import pekko.persistence.typed.state.scaladsl.Effect
    import pekko.persistence.typed.state.scaladsl.DurableStateBehavior.CommandHandler

    def apply(): Behavior[String] =
      Behaviors.setup { context =>
        DurableStateBehavior[String, State](
          persistenceId = PersistenceId.ofUniqueId("myPersistenceId"),
          emptyState = State(0),
          commandHandler = CommandHandler.command { cmd =>
            context.log.info("Got command {}", cmd)
            Effect.none
          })
      }
    // #actor-context
  }

  object TaggingBehavior {
    def apply(): Behavior[Command[_]] =
      // #tagging
      DurableStateBehavior[Command[_], State](
        persistenceId = PersistenceId.ofUniqueId("abc"),
        emptyState = State(0),
        commandHandler = (state, cmd) => throw new NotImplementedError("TODO: process the command & return an Effect"))
        .withTag("tag1")
    // #tagging
  }

  object WrapBehavior {
    import pekko.persistence.typed.state.scaladsl.Effect
    import pekko.persistence.typed.state.scaladsl.DurableStateBehavior.CommandHandler

    def apply(): Behavior[Command[_]] =
      // #wrapPersistentBehavior
      Behaviors.setup[Command[_]] { context =>
        DurableStateBehavior[Command[_], State](
          persistenceId = PersistenceId.ofUniqueId("abc"),
          emptyState = State(0),
          commandHandler = CommandHandler.command { cmd =>
            context.log.info("Got command {}", cmd)
            Effect.none
          })
      }
    // #wrapPersistentBehavior
  }
}
