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

import java.util.Optional;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.*;

public class OptionalBlogState {

  interface BlogEvent {}

  public record PostAdded(String postId, PostContent content) implements BlogEvent {}

  public record BodyChanged(String postId, String newBody) implements BlogEvent {}

  public record Published(String postId) implements BlogEvent {}

  public static class BlogState {
    final PostContent postContent;
    final boolean published;

    BlogState(PostContent postContent, boolean published) {
      this.postContent = postContent;
      this.published = published;
    }

    public BlogState withContent(PostContent newContent) {
      return new BlogState(newContent, this.published);
    }

    public String postId() {
      return postContent.postId();
    }
  }

  public interface BlogCommand {}

  public record AddPost(PostContent content, ActorRef<AddPostDone> replyTo)
      implements BlogCommand {}

  public record AddPostDone(String postId) implements BlogCommand {}

  public record GetPost(ActorRef<PostContent> replyTo) implements BlogCommand {}

  public record ChangeBody(String newBody, ActorRef<Done> replyTo) implements BlogCommand {}

  public record Publish(ActorRef<Done> replyTo) implements BlogCommand {}

  public record PostContent(String postId, String title, String body) implements BlogCommand {}

  public static class BlogBehavior
      extends EventSourcedBehavior<BlogCommand, BlogEvent, Optional<BlogState>> {

    private CommandHandlerBuilderByState<
            BlogCommand, BlogEvent, Optional<BlogState>, Optional<BlogState>>
        initialCommandHandler() {
      return newCommandHandlerBuilder()
          .forState(state -> !state.isPresent())
          .onCommand(
              AddPost.class,
              (state, cmd) -> {
                PostAdded event = new PostAdded(cmd.content().postId(), cmd.content());
                return Effect()
                    .persist(event)
                    .thenRun(() -> cmd.replyTo().tell(new AddPostDone(cmd.content().postId())));
              });
    }

    private CommandHandlerBuilderByState<
            BlogCommand, BlogEvent, Optional<BlogState>, Optional<BlogState>>
        postCommandHandler() {
      return newCommandHandlerBuilder()
          .forState(Optional::isPresent)
          .onCommand(
              ChangeBody.class,
              (state, cmd) -> {
                BodyChanged event = new BodyChanged(state.get().postId(), cmd.newBody());
                return Effect()
                    .persist(event)
                    .thenRun(() -> cmd.replyTo().tell(Done.getInstance()));
              })
          .onCommand(
              Publish.class,
              (state, cmd) ->
                  Effect()
                      .persist(new Published(state.get().postId()))
                      .thenRun(
                          () -> {
                            System.out.println("Blog post published: " + state.get().postId());
                            cmd.replyTo().tell(Done.getInstance());
                          }))
          .onCommand(
              GetPost.class,
              (state, cmd) -> {
                cmd.replyTo().tell(state.get().postContent);
                return Effect().none();
              })
          .onCommand(AddPost.class, (state, cmd) -> Effect().unhandled());
    }

    public BlogBehavior(PersistenceId persistenceId) {
      super(persistenceId);
    }

    @Override
    public Optional<BlogState> emptyState() {
      return Optional.empty();
    }

    @Override
    public CommandHandler<BlogCommand, BlogEvent, Optional<BlogState>> commandHandler() {
      return initialCommandHandler().orElse(postCommandHandler()).build();
    }

    @Override
    public EventHandler<Optional<BlogState>, BlogEvent> eventHandler() {

      EventHandlerBuilder<Optional<BlogState>, BlogEvent> builder = newEventHandlerBuilder();

      builder
          .forState(state -> !state.isPresent())
          .onEvent(
              PostAdded.class,
              (state, event) -> Optional.of(new BlogState(event.content(), false)));

      builder
          .forState(Optional::isPresent)
          .onEvent(
              BodyChanged.class,
              (state, chg) ->
                  state.map(
                      blogState ->
                          blogState.withContent(
                              new PostContent(
                                  blogState.postId(),
                                  blogState.postContent.title(),
                                  chg.newBody()))))
          .onEvent(
              Published.class,
              (state, event) -> state.map(blogState -> new BlogState(blogState.postContent, true)));

      return builder.build();
    }
  }
}
