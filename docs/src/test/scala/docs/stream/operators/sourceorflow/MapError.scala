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

package docs.stream.operators.sourceorflow

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ Sink, Source }

import scala.concurrent.ExecutionContext
import scala.util.control.NoStackTrace
import scala.util.{ Failure, Success }

object MapError extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher

  // #map-error
  Source(-1 to 1)
    .map(1 / _)
    .mapError {
      case _: ArithmeticException =>
        new UnsupportedOperationException("Divide by Zero Operation is not supported.") with NoStackTrace
    }
    .runWith(Sink.seq)
    .onComplete {
      case Success(value) => println(value.mkString)
      case Failure(ex)    => println(ex.getMessage)
    }

  // prints "Divide by Zero Operation is not supported."
  // #map-error

}
