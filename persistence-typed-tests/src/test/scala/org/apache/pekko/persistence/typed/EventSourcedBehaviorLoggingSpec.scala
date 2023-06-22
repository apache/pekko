/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.LoggingTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.typed.EventSourcedBehaviorLoggingSpec.ChattyEventSourcingBehavior.Hello
import pekko.persistence.typed.EventSourcedBehaviorLoggingSpec.ChattyEventSourcingBehavior.Hellos
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.serialization.jackson.CborSerializable
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.wordspec.AnyWordSpecLike

import pekko.Done
import pekko.actor.typed.ActorRef

object EventSourcedBehaviorLoggingSpec {

  object ChattyEventSourcingBehavior {
    sealed trait Command

    case class Hello(msg: String, replyTo: ActorRef[Done]) extends Command
    case class Hellos(msg1: String, msg2: String, replyTo: ActorRef[Done]) extends Command

    final case class Event(msg: String) extends CborSerializable

    def apply(id: PersistenceId): Behavior[Command] = {
      Behaviors.setup { ctx =>
        EventSourcedBehavior[Command, Event, Set[Event]](
          id,
          Set.empty,
          (_, command) =>
            command match {
              case Hello(msg, replyTo) =>
                ctx.log.info("received message '{}'", msg)
                Effect.persist(Event(msg)).thenReply(replyTo)(_ => Done)

              case Hellos(msg1, msg2, replyTo) =>
                Effect.persist(Event(msg1), Event(msg2)).thenReply(replyTo)(_ => Done)
            },
          (state, event) => state + event)
      }
    }
  }
}

abstract class EventSourcedBehaviorLoggingSpec(config: Config)
    extends ScalaTestWithActorTestKit(config)
    with AnyWordSpecLike
    with LogCapturing {
  import EventSourcedBehaviorLoggingSpec._

  def loggerName: String
  def loggerId: String

  s"Chatty behavior ($loggerId)" must {
    val myId = PersistenceId("Chatty", "chat-1")
    val chattyActor = spawn(ChattyEventSourcingBehavior(myId))

    "always log user message in context.log" in {
      val doneProbe = createTestProbe[Done]()
      LoggingTestKit
        .info("received message 'Mary'")
        .withLoggerName(
          "org.apache.pekko.persistence.typed.EventSourcedBehaviorLoggingSpec$ChattyEventSourcingBehavior$")
        .expect {
          chattyActor ! Hello("Mary", doneProbe.ref)
          doneProbe.receiveMessage()
        }
    }

    s"log internal messages in '$loggerId' logger without logging user data (Persist)" in {
      val doneProbe = createTestProbe[Done]()
      LoggingTestKit
        .debug(
          "Handled command [org.apache.pekko.persistence.typed.EventSourcedBehaviorLoggingSpec$ChattyEventSourcingBehavior$Hello], " +
          "resulting effect: [Persist(org.apache.pekko.persistence.typed.EventSourcedBehaviorLoggingSpec$ChattyEventSourcingBehavior$Event)], side effects: [1]")
        .withLoggerName(loggerName)
        .expect {
          chattyActor ! Hello("Joe", doneProbe.ref)
          doneProbe.receiveMessage()
        }
    }

    s"log internal messages in '$loggerId' logger without logging user data (PersistAll)" in {
      val doneProbe = createTestProbe[Done]()
      LoggingTestKit
        .debug(
          "Handled command [org.apache.pekko.persistence.typed.EventSourcedBehaviorLoggingSpec$ChattyEventSourcingBehavior$Hellos], " +
          "resulting effect: [PersistAll(org.apache.pekko.persistence.typed.EventSourcedBehaviorLoggingSpec$ChattyEventSourcingBehavior$Event," +
          "org.apache.pekko.persistence.typed.EventSourcedBehaviorLoggingSpec$ChattyEventSourcingBehavior$Event)], side effects: [1]")
        .withLoggerName(loggerName)
        .expect {
          chattyActor ! Hellos("Mary", "Joe", doneProbe.ref)
          doneProbe.receiveMessage()
        }
    }

    s"log in '$loggerId' while preserving MDC source" in {
      val doneProbe = createTestProbe[Done]()
      LoggingTestKit
        .debug("Handled command ")
        .withLoggerName(loggerName)
        .withMdc(Map("persistencePhase" -> "running-cmd", "persistenceId" -> "Chatty|chat-1"))
        .expect {
          chattyActor ! Hello("Mary", doneProbe.ref)
          doneProbe.receiveMessage()
        }
    }
  }
}

class EventSourcedBehaviorLoggingInternalLoggerSpec
    extends EventSourcedBehaviorLoggingSpec(PersistenceTestKitPlugin.config) {
  override def loggerName = "org.apache.pekko.persistence.typed.internal.EventSourcedBehaviorImpl"
  override def loggerId = "internal.log"
}

object EventSourcedBehaviorLoggingContextLoggerSpec {
  val config =
    ConfigFactory
      .parseString("pekko.persistence.typed.use-context-logger-for-internal-logging = true")
      .withFallback(PersistenceTestKitPlugin.config)
}
class EventSourcedBehaviorLoggingContextLoggerSpec
    extends EventSourcedBehaviorLoggingSpec(EventSourcedBehaviorLoggingContextLoggerSpec.config) {
  override def loggerName =
    "org.apache.pekko.persistence.typed.EventSourcedBehaviorLoggingSpec$ChattyEventSourcingBehavior$"
  override def loggerId = "context.log"
}
