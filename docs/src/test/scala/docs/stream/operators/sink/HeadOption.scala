/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sink

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ Sink, Source }

import scala.concurrent.{ ExecutionContextExecutor, Future }

object HeadOption {
  implicit val system: ActorSystem = ???
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  def headOptionExample(): Unit = {
    // #headoption
    val source = Source.empty
    val result: Future[Option[Int]] = source.runWith(Sink.headOption)
    result.foreach(println)
    // None
    // #headoption
  }
}
