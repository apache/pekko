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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;

public class MovieWatchList
    extends EventSourcedBehavior<
        MovieWatchList.Command, MovieWatchList.Event, MovieWatchList.MovieList> {

  interface Command {}

  public record AddMovie(String movieId) implements Command {}

  public record RemoveMovie(String movieId) implements Command {}

  interface Event {}

  public record MovieAdded(String movieId) implements Event {}

  public record MovieRemoved(String movieId) implements Event {}

  public record GetMovieList(ActorRef<MovieList> replyTo) implements Command {}

  public static class MovieList {
    public final Set<String> movieIds;

    public MovieList(Set<String> movieIds) {
      this.movieIds = Set.copyOf(movieIds);
    }

    public MovieList add(String movieId) {
      Set<String> newSet = new HashSet<>(movieIds);
      newSet.add(movieId);
      return new MovieList(newSet);
    }

    public MovieList remove(String movieId) {
      Set<String> newSet = new HashSet<>(movieIds);
      newSet.remove(movieId);
      return new MovieList(newSet);
    }
  }

  public static Behavior<Command> behavior(String userId) {
    return new MovieWatchList(PersistenceId.ofUniqueId("movies-" + userId));
  }

  public MovieWatchList(PersistenceId persistenceId) {
    super(persistenceId);
  }

  @Override
  public MovieList emptyState() {
    return new MovieList(Collections.emptySet());
  }

  @Override
  public CommandHandler<Command, Event, MovieList> commandHandler() {
    return newCommandHandlerBuilder()
        .forAnyState()
        .onCommand(
            AddMovie.class,
            (state, cmd) -> {
              return Effect().persist(new MovieAdded(cmd.movieId()));
            })
        .onCommand(
            RemoveMovie.class,
            (state, cmd) -> {
              return Effect().persist(new MovieRemoved(cmd.movieId()));
            })
        .onCommand(
            GetMovieList.class,
            (state, cmd) -> {
              cmd.replyTo().tell(state);
              return Effect().none();
            })
        .build();
  }

  @Override
  public EventHandler<MovieList, Event> eventHandler() {
    return newEventHandlerBuilder()
        .forAnyState()
        .onEvent(MovieAdded.class, (state, event) -> state.add(event.movieId()))
        .onEvent(MovieRemoved.class, (state, event) -> state.remove(event.movieId()))
        .build();
  }
}
