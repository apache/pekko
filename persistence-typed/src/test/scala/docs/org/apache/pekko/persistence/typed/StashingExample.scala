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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.SupervisorStrategy
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior

object StashingExample {

  // #stashing
  object TaskManager {

    sealed trait Command
    final case class StartTask(taskId: String) extends Command
    final case class NextStep(taskId: String, instruction: String) extends Command
    final case class EndTask(taskId: String) extends Command

    sealed trait Event
    final case class TaskStarted(taskId: String) extends Event
    final case class TaskStep(taskId: String, instruction: String) extends Event
    final case class TaskCompleted(taskId: String) extends Event

    final case class State(taskIdInProgress: Option[String])

    def apply(persistenceId: PersistenceId): Behavior[Command] =
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State(None),
        commandHandler = (state, command) => onCommand(state, command),
        eventHandler = (state, event) => applyEvent(state, event))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(1.second, 30.seconds, 0.2))

    private def onCommand(state: State, command: Command): Effect[Event, State] =
      state.taskIdInProgress match {
        case None =>
          command match {
            case StartTask(taskId) =>
              Effect.persist(TaskStarted(taskId))
            case _ =>
              Effect.unhandled
          }

        case Some(inProgress) =>
          command match {
            case StartTask(taskId) =>
              if (inProgress == taskId)
                Effect.none // duplicate, already in progress
              else
                // other task in progress, wait with new task until later
                Effect.stash()

            case NextStep(taskId, instruction) =>
              if (inProgress == taskId)
                Effect.persist(TaskStep(taskId, instruction))
              else
                // other task in progress, wait with new task until later
                Effect.stash()

            case EndTask(taskId) =>
              if (inProgress == taskId)
                Effect.persist(TaskCompleted(taskId)).thenUnstashAll() // continue with next task
              else
                // other task in progress, wait with new task until later
                Effect.stash()
          }
      }

    private def applyEvent(state: State, event: Event): State =
      event match {
        case TaskStarted(taskId) => State(Option(taskId))
        case TaskStep(_, _)      => state
        case TaskCompleted(_)    => State(None)
      }
  }
  // #stashing
}
