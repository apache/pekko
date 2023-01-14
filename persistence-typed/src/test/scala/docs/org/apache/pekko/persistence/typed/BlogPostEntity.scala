/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
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
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior

//#behavior
object BlogPostEntity {
  // commands, events, state defined here

  // #behavior

  // #event
  sealed trait Event
  final case class PostAdded(postId: String, content: PostContent) extends Event

  final case class BodyChanged(postId: String, newBody: String) extends Event
  final case class Published(postId: String) extends Event
  // #event

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
      context.log.info("Starting BlogPostEntity {}", entityId)
      EventSourcedBehavior[Command, Event, State](persistenceId, emptyState = BlankState, commandHandler, eventHandler)
    }
  }
  // #behavior

  // #command-handler
  private val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
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
            Effect.unhandled.thenRun(_ => replyTo ! StatusReply.Error("Cannot add post while in draft state"))
        }

      case publishedState: PublishedState =>
        command match {
          case GetPost(replyTo) => getPost(publishedState, replyTo)
          case AddPost(_, replyTo) =>
            Effect.unhandled.thenRun(_ => replyTo ! StatusReply.Error("Cannot add post, already published"))
          case _ => Effect.unhandled
        }
    }
  }

  private def addPost(cmd: AddPost): Effect[Event, State] = {
    // #reply
    val evt = PostAdded(cmd.content.postId, cmd.content)
    Effect.persist(evt).thenRun { _ =>
      // After persist is done additional side effects can be performed
      cmd.replyTo ! StatusReply.Success(AddPostDone(cmd.content.postId))
    }
    // #reply
  }

  private def changeBody(state: DraftState, cmd: ChangeBody): Effect[Event, State] = {
    val evt = BodyChanged(state.postId, cmd.newBody)
    Effect.persist(evt).thenRun { _ =>
      cmd.replyTo ! Done
    }
  }

  private def publish(state: DraftState, replyTo: ActorRef[Done]): Effect[Event, State] = {
    Effect.persist(Published(state.postId)).thenRun { _ =>
      println(s"Blog post ${state.postId} was published")
      replyTo ! Done
    }
  }

  private def getPost(state: DraftState, replyTo: ActorRef[PostContent]): Effect[Event, State] = {
    replyTo ! state.content
    Effect.none
  }

  private def getPost(state: PublishedState, replyTo: ActorRef[PostContent]): Effect[Event, State] = {
    replyTo ! state.content
    Effect.none
  }
  // #command-handler

  // #event-handler
  private val eventHandler: (State, Event) => State = { (state, event) =>
    state match {

      case BlankState =>
        event match {
          case PostAdded(_, content) =>
            DraftState(content)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }

      case draftState: DraftState =>
        event match {

          case BodyChanged(_, newBody) =>
            draftState.withBody(newBody)

          case Published(_) =>
            PublishedState(draftState.content)

          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }

      case _: PublishedState =>
        // no more changes after published
        throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }
  // #event-handler

  // #behavior

  // commandHandler and eventHandler defined here
}
//#behavior
