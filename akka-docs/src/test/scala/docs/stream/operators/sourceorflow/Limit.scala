/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future

object Limit {

  implicit val system: ActorSystem[_] = ???

  def simple(): Unit = {
    // #simple
    val untrustedSource: Source[String, NotUsed] = Source.repeat("element")

    val elements: Future[Seq[String]] =
      untrustedSource.limit(10000).runWith(Sink.seq)
    // #simple
  }

}
