/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.typed

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.ActorTags
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.LoggerFactory

object LoggingDocExamples {

  object BackendManager {
    sealed trait Command {
      def identifier: String
    }
    def apply(): Behavior[Command] = Behaviors.empty
  }

  def howToUse(): Unit = {

    // #context-log
    Behaviors.receive[String] { (context, message) =>
      context.log.info("Received message: {}", message)
      Behaviors.same
    }
    // #context-log

    // #logger-name
    Behaviors.setup[String] { context =>
      context.setLoggerName("com.myservice.BackendManager")
      context.log.info("Starting up")

      Behaviors.receiveMessage { message =>
        context.log.debug("Received message: {}", message)
        Behaviors.same
      }
    }
    // #logger-name

    // #logger-factory
    val log = LoggerFactory.getLogger("com.myservice.BackendTask")

    Future {
      // some work
      "result"
    }.onComplete {
      case Success(result) => log.info("Task completed: {}", result)
      case Failure(exc)    => log.error("Task failed", exc)
    }
    // #logger-factory

  }

  def placeholders(): Unit = {
    // #info2
    import org.apache.pekko.actor.typed.scaladsl.LoggerOps

    Behaviors.receive[String] { (context, message) =>
      context.log.info2("{} received message: {}", context.self.path.name, message)
      Behaviors.same
    }
    // #info2

    // #infoN
    import org.apache.pekko.actor.typed.scaladsl.LoggerOps

    Behaviors.receive[String] { (context, message) =>
      context.log.infoN(
        "{} received message of size {} starting with: {}",
        context.self.path.name,
        message.length,
        message.take(10))
      Behaviors.same
    }
    // #infoN

  }

  def logMessages(): Unit = {
    // #logMessages
    import org.apache.pekko.actor.typed.LogOptions
    import org.slf4j.event.Level

    Behaviors.logMessages(LogOptions().withLevel(Level.TRACE), BackendManager())
    // #logMessages
  }

  def withMdc(): Unit = {
    val system: ActorSystem[_] = ???

    // #withMdc
    val staticMdc = Map("startTime" -> system.startTime.toString)
    Behaviors.withMdc[BackendManager.Command](
      staticMdc,
      mdcForMessage =
        (msg: BackendManager.Command) => Map("identifier" -> msg.identifier, "upTime" -> system.uptime.toString)) {
      BackendManager()
    }
    // #withMdc
  }

  def logging(): Unit = {
    implicit val system: ActorSystem[_] = ???
    final case class Message(s: String)
    val ref: ActorRef[Message] = ???

    // #test-logging
    import org.apache.pekko.actor.testkit.typed.scaladsl.LoggingTestKit

    // implicit ActorSystem is needed, but that is given by ScalaTestWithActorTestKit
    // implicit val system: ActorSystem[_]

    LoggingTestKit.info("Received message").expect {
      ref ! Message("hello")
    }
    // #test-logging

    // #test-logging-criteria
    LoggingTestKit
      .error[IllegalArgumentException]
      .withMessageRegex(".*was rejected.*expecting ascii input.*")
      .withCustom { event =>
        event.marker match {
          case Some(m) => m.getName == "validation"
          case None    => false
        }
      }
      .withOccurrences(2)
      .expect {
        ref ! Message("hellö")
        ref ! Message("hejdå")
      }
    // #test-logging-criteria
  }

  def tagsExample(): Unit =
    Behaviors.setup[AnyRef] { context =>
      val myBehavior = Behaviors.empty[AnyRef]
      // #tags
      context.spawn(myBehavior, "MyActor", ActorTags("processing"))
      // #tags
      Behaviors.stopped
    }

}
