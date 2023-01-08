/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.persistence.typed;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.persistence.testkit.query.javadsl.PersistenceTestKitReadJournal;
import org.apache.pekko.persistence.typed.ReplicaId;
import org.apache.pekko.persistence.typed.ReplicationId;
import org.apache.pekko.persistence.typed.crdt.ORSet;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.ReplicatedEventSourcedBehavior;
import org.apache.pekko.persistence.typed.javadsl.ReplicatedEventSourcing;
import org.apache.pekko.persistence.typed.javadsl.ReplicationContext;

import java.util.Collections;
import java.util.Set;

interface ReplicatedMovieExample {

  // #movie-entity
  public final class MovieWatchList
      extends ReplicatedEventSourcedBehavior<MovieWatchList.Command, ORSet.DeltaOp, ORSet<String>> {

    interface Command {}

    public static class AddMovie implements Command {
      public final String movieId;

      public AddMovie(String movieId) {
        this.movieId = movieId;
      }
    }

    public static class RemoveMovie implements Command {
      public final String movieId;

      public RemoveMovie(String movieId) {
        this.movieId = movieId;
      }
    }

    public static class GetMovieList implements Command {
      public final ActorRef<MovieList> replyTo;

      public GetMovieList(ActorRef<MovieList> replyTo) {
        this.replyTo = replyTo;
      }
    }

    public static class MovieList {
      public final Set<String> movieIds;

      public MovieList(Set<String> movieIds) {
        this.movieIds = Collections.unmodifiableSet(movieIds);
      }
    }

    public static Behavior<Command> create(
        String entityId, ReplicaId replicaId, Set<ReplicaId> allReplicas) {
      return ReplicatedEventSourcing.commonJournalConfig(
          new ReplicationId("movies", entityId, replicaId),
          allReplicas,
          PersistenceTestKitReadJournal.Identifier(),
          MovieWatchList::new);
    }

    private MovieWatchList(ReplicationContext replicationContext) {
      super(replicationContext);
    }

    @Override
    public ORSet<String> emptyState() {
      return ORSet.empty(getReplicationContext().replicaId());
    }

    @Override
    public CommandHandler<Command, ORSet.DeltaOp, ORSet<String>> commandHandler() {
      return newCommandHandlerBuilder()
          .forAnyState()
          .onCommand(
              AddMovie.class, (state, command) -> Effect().persist(state.add(command.movieId)))
          .onCommand(
              RemoveMovie.class,
              (state, command) -> Effect().persist(state.remove(command.movieId)))
          .onCommand(
              GetMovieList.class,
              (state, command) -> {
                command.replyTo.tell(new MovieList(state.getElements()));
                return Effect().none();
              })
          .build();
    }

    @Override
    public EventHandler<ORSet<String>, ORSet.DeltaOp> eventHandler() {
      return newEventHandlerBuilder().forAnyState().onAnyEvent(ORSet::applyOperation);
    }
  }
  // #movie-entity
}
