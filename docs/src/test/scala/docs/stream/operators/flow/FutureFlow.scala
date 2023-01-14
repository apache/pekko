/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.flow

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future

class FutureFlow {

  implicit val system: ActorSystem = ???
  import system.dispatcher

  def compileOnlyBaseOnFirst(): Unit = {
    // #base-on-first-element
    def processingFlow(id: Int): Future[Flow[Int, String, NotUsed]] =
      Future {
        Flow[Int].map(n => s"id: $id, value: $n")
      }

    val source: Source[String, NotUsed] =
      Source(1 to 10).prefixAndTail(1).flatMapConcat {
        case (List(id: Int), tail) =>
          // base the Future flow creation on the first element
          tail.via(Flow.futureFlow(processingFlow(id)))
      }
    // #base-on-first-element
  }

}
