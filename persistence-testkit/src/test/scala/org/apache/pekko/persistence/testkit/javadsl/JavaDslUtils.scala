/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.javadsl

import java.util

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.persistence.testkit.{ Cmd, CommonUtils, EmptyState, Evt, Passivate, Recovered, Stopped, TestCommand }
import pekko.persistence.typed.{ PersistenceId, RecoveryCompleted }
import pekko.persistence.typed.javadsl.{ CommandHandler, EventHandler, EventSourcedBehavior, SignalHandler }

trait JavaDslUtils extends CommonUtils {

  def eventSourcedBehavior(
      pid: String,
      setConstantTag: Boolean = false,
      replyOnRecovery: Option[ActorRef[Any]] = None) =
    new EventSourcedBehavior[TestCommand, Evt, EmptyState](PersistenceId.ofUniqueId(pid)) {

      override protected def emptyState: EmptyState = EmptyState()

      override protected def commandHandler(): CommandHandler[TestCommand, Evt, EmptyState] =
        newCommandHandlerBuilder()
          .forAnyState()
          .onAnyCommand((command: TestCommand) => {
            command match {
              case Cmd(data) => Effect.persist(Evt(data))
              case Passivate => Effect.stop().thenRun((_: EmptyState) => replyOnRecovery.foreach(_ ! Stopped))
            }
          })

      override protected def eventHandler(): EventHandler[EmptyState, Evt] =
        newEventHandlerBuilder().forAnyState().onAnyEvent(_ => emptyState)

      override def shouldSnapshot(state: EmptyState, event: Evt, sequenceNr: Long): Boolean = true

      override def signalHandler(): SignalHandler[EmptyState] =
        newSignalHandlerBuilder().onSignal(RecoveryCompleted, _ => replyOnRecovery.foreach(_ ! Recovered)).build

      override def tagsFor(event: Evt): util.Set[String] = {
        if (setConstantTag) {
          util.Collections.singleton("tag")
        } else {
          super.tagsFor(event)
        }
      }
    }

}
