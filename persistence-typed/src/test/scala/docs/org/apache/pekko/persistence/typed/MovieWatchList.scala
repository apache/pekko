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

package docs.org.apache.pekko.persistence.typed

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.persistence.typed.scaladsl.EventSourcedBehavior.CommandHandler

object MovieWatchList {
  sealed trait Command
  final case class AddMovie(movieId: String) extends Command
  final case class RemoveMovie(movieId: String) extends Command
  final case class GetMovieList(replyTo: ActorRef[MovieList]) extends Command

  sealed trait Event
  final case class MovieAdded(movieId: String) extends Event
  final case class MovieRemoved(movieId: String) extends Event

  final case class MovieList(movieIds: Set[String]) {
    def applyEvent(event: Event): MovieList =
      event match {
        case MovieAdded(movieId)   => copy(movieIds = movieIds + movieId)
        case MovieRemoved(movieId) => copy(movieIds = movieIds + movieId)
      }
  }

  private val commandHandler: CommandHandler[Command, Event, MovieList] = { (state, cmd) =>
    cmd match {
      case AddMovie(movieId) =>
        Effect.persist(MovieAdded(movieId))
      case RemoveMovie(movieId) =>
        Effect.persist(MovieRemoved(movieId))
      case GetMovieList(replyTo) =>
        replyTo ! state
        Effect.none
    }
  }

  def behavior(userId: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, MovieList](
      persistenceId = PersistenceId("movies", userId),
      emptyState = MovieList(Set.empty),
      commandHandler,
      eventHandler = (state, event) => state.applyEvent(event))

}
