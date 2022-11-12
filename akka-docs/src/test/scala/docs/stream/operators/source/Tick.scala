/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.source

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.Cancellable
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

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
