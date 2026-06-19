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
import org.apache.pekko.persistence.typed.state.javadsl.*;

// #behavior
public class BlogPostEntityDurableState
    extends DurableStateBehavior<
        BlogPostEntityDurableState.Command, BlogPostEntityDurableState.State> {
  // commands and state as in above snippets

  // #behavior

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
          context.getLog().info("Starting BlogPostEntityDurableState {}", entityId);
          return new BlogPostEntityDurableState(persistenceId);
        });
  }

  private BlogPostEntityDurableState(PersistenceId persistenceId) {
    super(persistenceId);
  }

  @Override
  public State emptyState() {
    return BlankState.INSTANCE;
  }

  // #behavior

  // #command-handler
  @Override
  public CommandHandler<Command, State> commandHandler() {
    CommandHandlerBuilder<Command, State> builder = newCommandHandlerBuilder();

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

  private Effect<State> onAddPost(AddPost cmd) {
    // #reply
    return Effect()
        .persist(new DraftState(cmd.content()))
        .thenRun(() -> cmd.replyTo().tell(new AddPostDone(cmd.content().postId())));
    // #reply
  }

  private Effect<State> onChangeBody(DraftState state, ChangeBody cmd) {
    return Effect()
        .persist(state.withBody(cmd.newBody()))
        .thenRun(() -> cmd.replyTo().tell(Done.getInstance()));
  }

  private Effect<State> onChangeBody(PublishedState state, ChangeBody cmd) {
    return Effect()
        .persist(state.withBody(cmd.newBody()))
        .thenRun(() -> cmd.replyTo().tell(Done.getInstance()));
  }

  private Effect<State> onPublish(DraftState state, Publish cmd) {
    return Effect()
        .persist(new PublishedState(state.content))
        .thenRun(
            () -> {
              System.out.println("Blog post published: " + state.postId());
              cmd.replyTo().tell(Done.getInstance());
            });
  }

  private Effect<State> onGetPost(DraftState state, GetPost cmd) {
    cmd.replyTo().tell(state.content);
    return Effect().none();
  }

  private Effect<State> onGetPost(PublishedState state, GetPost cmd) {
    cmd.replyTo().tell(state.content);
    return Effect().none();
  }
  // #command-handler

  // #behavior
  // commandHandler, eventHandler as in above snippets
}
  // #behavior
