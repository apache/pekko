/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object WatchTermination {

  def watchTerminationExample(): Unit = {
    implicit val system: ActorSystem = ???
    implicit val ec: ExecutionContext = ???

    // #watchTermination
    Source(1 to 5)
      .watchTermination()((prevMatValue, future) =>
        // this function will be run when the stream terminates
        // the Future provided as a second parameter indicates whether the stream completed successfully or failed
        future.onComplete {
          case Failure(exception) => println(exception.getMessage)
          case Success(_)         => println(s"The stream materialized $prevMatValue")
        })
      .runForeach(println)
    /*
    Prints:
    1
    2
    3
    4
    5
    The stream materialized NotUsed
     */

    Source(1 to 5)
      .watchTermination()((prevMatValue, future) =>
        future.onComplete {
          case Failure(exception) => println(exception.getMessage)
          case Success(_)         => println(s"The stream materialized $prevMatValue")
        })
      .runForeach(e => if (e == 3) throw new Exception("Boom") else println(e))
    /*
    Prints:
    1
    2
    Boom
     */
    // #watchTermination
  }
}
