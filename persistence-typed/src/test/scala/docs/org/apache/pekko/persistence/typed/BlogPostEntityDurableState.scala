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

package docs.org.apache.pekko.persistence.typed

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.pattern.StatusReply
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.state.scaladsl.Effect
import pekko.persistence.typed.state.scaladsl.DurableStateBehavior

//#behavior
object BlogPostEntityDurableState {
  // commands, state defined here

  // #behavior

  // #state
  sealed trait State

  case object BlankState extends State

  final case class DraftState(content: PostContent) extends State {
    def withBody(newBody: String): DraftState =
      copy(content = content.copy(body = newBody))

    def postId: String = content.postId
  }

  final case class PublishedState(content: PostContent) extends State {
    def postId: String = content.postId
  }
  // #state

  // #commands
  sealed trait Command
  // #reply-command
  final case class AddPost(content: PostContent, replyTo: ActorRef[StatusReply[AddPostDone]]) extends Command
  final case class AddPostDone(postId: String)
  // #reply-command
  final case class GetPost(replyTo: ActorRef[PostContent]) extends Command
  final case class ChangeBody(newBody: String, replyTo: ActorRef[Done]) extends Command
  final case class Publish(replyTo: ActorRef[Done]) extends Command
  final case class PostContent(postId: String, title: String, body: String)
  // #commands

  // #behavior
  def apply(entityId: String, persistenceId: PersistenceId): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Starting BlogPostEntityDurableState {}", entityId)
      DurableStateBehavior[Command, State](persistenceId, emptyState = BlankState, commandHandler)
    }
  }
  // #behavior

  // #command-handler
  private val commandHandler: (State, Command) => Effect[State] = { (state, command) =>
    state match {

      case BlankState =>
        command match {
          case cmd: AddPost => addPost(cmd)
          case _            => Effect.unhandled
        }

      case draftState: DraftState =>
        command match {
          case cmd: ChangeBody  => changeBody(draftState, cmd)
          case Publish(replyTo) => publish(draftState, replyTo)
          case GetPost(replyTo) => getPost(draftState, replyTo)
          case AddPost(_, replyTo) =>
            Effect.unhandled[State].thenRun(_ => replyTo ! StatusReply.Error("Cannot add post while in draft state"))
        }

      case publishedState: PublishedState =>
        command match {
          case GetPost(replyTo) => getPost(publishedState, replyTo)
          case AddPost(_, replyTo) =>
            Effect.unhandled[State].thenRun(_ => replyTo ! StatusReply.Error("Cannot add post, already published"))
          case _ => Effect.unhandled
        }
    }
  }

  private def addPost(cmd: AddPost): Effect[State] = {
    // #reply
    Effect.persist(DraftState(cmd.content)).thenRun { _ =>
      // After persist is done additional side effects can be performed
      cmd.replyTo ! StatusReply.Success(AddPostDone(cmd.content.postId))
    }
    // #reply
  }

  private def changeBody(state: DraftState, cmd: ChangeBody): Effect[State] = {
    Effect.persist(state.withBody(cmd.newBody)).thenRun { _ =>
      cmd.replyTo ! Done
    }
  }

  private def publish(state: DraftState, replyTo: ActorRef[Done]): Effect[State] = {
    Effect.persist(PublishedState(state.content)).thenRun { _ =>
      println(s"Blog post ${state.postId} was published")
      replyTo ! Done
    }
  }

  private def getPost(state: DraftState, replyTo: ActorRef[PostContent]): Effect[State] = {
    replyTo ! state.content
    Effect.none
  }

  private def getPost(state: PublishedState, replyTo: ActorRef[PostContent]): Effect[State] = {
    replyTo ! state.content
    Effect.none
  }
  // #command-handler
  // #behavior

  // commandHandler defined here
}
//#behavior
