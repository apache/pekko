/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.persistence.typed;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.*;

// #behavior
public class BlogPostEntity
    extends EventSourcedBehavior<
        BlogPostEntity.Command, BlogPostEntity.Event, BlogPostEntity.State> {
  // commands, events and state as in above snippets

  // #behavior

  // #event
  public interface Event {}

  public record PostAdded(String postId, PostContent content) implements Event {}

  public record BodyChanged(String postId, String newBody) implements Event {}

  public record Published(String postId) implements Event {}

  // #event

  // #state
  interface State {}

  enum BlankState implements State {
    INSTANCE
  }

  static class DraftState implements State {
    final PostContent content;

    DraftState(PostContent content) {
      this.content = content;
    }

    DraftState withContent(PostContent newContent) {
      return new DraftState(newContent);
    }

    DraftState withBody(String newBody) {
      return withContent(new PostContent(postId(), content.title(), newBody));
    }

    String postId() {
      return content.postId();
    }
  }

  static class PublishedState implements State {
    final PostContent content;

    PublishedState(PostContent content) {
      this.content = content;
    }

    PublishedState withContent(PostContent newContent) {
      return new PublishedState(newContent);
    }

    PublishedState withBody(String newBody) {
      return withContent(new PostContent(postId(), content.title(), newBody));
    }

    String postId() {
      return content.postId();
    }
  }

  // #state

  // #commands
  public interface Command {}

  // #reply-command
  public record AddPost(PostContent content, ActorRef<AddPostDone> replyTo) implements Command {}

  public record AddPostDone(String postId) implements Command {}

  // #reply-command
  public record GetPost(ActorRef<PostContent> replyTo) implements Command {}

  public record ChangeBody(String newBody, ActorRef<Done> replyTo) implements Command {}

  public record Publish(ActorRef<Done> replyTo) implements Command {}

  public record PostContent(String postId, String title, String body) implements Command {}

  // #commands

  // #behavior
  public static Behavior<Command> create(String entityId, PersistenceId persistenceId) {
    return Behaviors.setup(
        context -> {
          context.getLog().info("Starting BlogPostEntity {}", entityId);
          return new BlogPostEntity(persistenceId);
        });
  }

  private BlogPostEntity(PersistenceId persistenceId) {
    super(persistenceId);
  }

  @Override
  public State emptyState() {
    return BlankState.INSTANCE;
  }

  // #behavior

  // #command-handler
  @Override
  public CommandHandler<Command, Event, State> commandHandler() {
    CommandHandlerBuilder<Command, Event, State> builder = newCommandHandlerBuilder();

    builder.forStateType(BlankState.class).onCommand(AddPost.class, this::onAddPost);

    builder
        .forStateType(DraftState.class)
        .onCommand(ChangeBody.class, this::onChangeBody)
        .onCommand(Publish.class, this::onPublish)
        .onCommand(GetPost.class, this::onGetPost);

    builder
        .forStateType(PublishedState.class)
        .onCommand(ChangeBody.class, this::onChangeBody)
        .onCommand(GetPost.class, this::onGetPost);

    builder.forAnyState().onCommand(AddPost.class, (state, cmd) -> Effect().unhandled());

    return builder.build();
  }

  private Effect<Event, State> onAddPost(AddPost cmd) {
    // #reply
    PostAdded event = new PostAdded(cmd.content().postId(), cmd.content());
    return Effect()
        .persist(event)
        .thenRun(() -> cmd.replyTo().tell(new AddPostDone(cmd.content().postId())));
    // #reply
  }

  private Effect<Event, State> onChangeBody(DraftState state, ChangeBody cmd) {
    BodyChanged event = new BodyChanged(state.postId(), cmd.newBody());
    return Effect().persist(event).thenRun(() -> cmd.replyTo().tell(Done.getInstance()));
  }

  private Effect<Event, State> onChangeBody(PublishedState state, ChangeBody cmd) {
    BodyChanged event = new BodyChanged(state.postId(), cmd.newBody());
    return Effect().persist(event).thenRun(() -> cmd.replyTo().tell(Done.getInstance()));
  }

  private Effect<Event, State> onPublish(DraftState state, Publish cmd) {
    return Effect()
        .persist(new Published(state.postId()))
        .thenRun(
            () -> {
              System.out.println("Blog post published: " + state.postId());
              cmd.replyTo().tell(Done.getInstance());
            });
  }

  private Effect<Event, State> onGetPost(DraftState state, GetPost cmd) {
    cmd.replyTo().tell(state.content);
    return Effect().none();
  }

  private Effect<Event, State> onGetPost(PublishedState state, GetPost cmd) {
    cmd.replyTo().tell(state.content);
    return Effect().none();
  }

  // #command-handler

  // #event-handler
  @Override
  public EventHandler<State, Event> eventHandler() {

    EventHandlerBuilder<State, Event> builder = newEventHandlerBuilder();

    builder
        .forStateType(BlankState.class)
        .onEvent(PostAdded.class, event -> new DraftState(event.content()));

    builder
        .forStateType(DraftState.class)
        .onEvent(BodyChanged.class, (state, chg) -> state.withBody(chg.newBody()))
        .onEvent(Published.class, (state, event) -> new PublishedState(state.content));

    builder
        .forStateType(PublishedState.class)
        .onEvent(BodyChanged.class, (state, chg) -> state.withBody(chg.newBody()));

    return builder.build();
  }
  // #event-handler

  // #behavior
  // commandHandler, eventHandler as in above snippets
}
  // #behavior
