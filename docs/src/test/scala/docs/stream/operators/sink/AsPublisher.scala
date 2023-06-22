/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sink

import scala.concurrent.{ ExecutionContextExecutor, Future }
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ Sink, Source }

object AsPublisher {
  implicit val system: ActorSystem = ???
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  def asPublisherExample() = {
    def asPublisherExample() = {
      // #asPublisher
      val source = Source(1 to 5)

      val publisher = source.runWith(Sink.asPublisher(false))
      Source.fromPublisher(publisher).runWith(Sink.foreach(println)) // 1 2 3 4 5
      Source
        .fromPublisher(publisher)
        .runWith(Sink.foreach(println)) // No output, because the source was not able to subscribe to the publisher.
      // #asPublisher
    }
  }
}
