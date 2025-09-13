/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.source

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.Cancellable
import pekko.actor.typed.{ ActorRef, ActorSystem }
import pekko.stream.scaladsl.Source
import pekko.actor.typed.scaladsl.AskPattern._
import pekko.stream.scaladsl.Flow
import pekko.util.Timeout

object Tick {
  // not really a runnable example, these are just pretend
  implicit val system: ActorSystem[Nothing] = null
  val myActor: ActorRef[MyActor.Command] = null;

  object MyActor {
    sealed trait Command {}
    case class Query(replyTo: ActorRef[Response]) extends Command
    case class Response(text: String)
  }

  def simple(): Unit = {
    // #simple
    Source
      .tick(
        1.second, // delay of first tick
        1.second, // delay of subsequent ticks
        "tick" // element emitted each tick
      )
      .runForeach(println)
    // #simple
  }

  def pollSomething(): Unit = {
    // #poll-actor
    val periodicActorResponse: Source[String, Cancellable] = Source
      .tick(1.second, 1.second, "tick")
      .mapAsync(1) { _ =>
        implicit val timeout: Timeout = 3.seconds
        val response: Future[MyActor.Response] = myActor.ask(MyActor.Query(_))
        response
      }
      .map(_.text);
    // #poll-actor

    // #zip-latest
    val zipWithLatestResponse: Flow[Int, (Int, String), NotUsed] =
      Flow[Int].zipLatest(periodicActorResponse);
    // #zip-latest
  }
}
