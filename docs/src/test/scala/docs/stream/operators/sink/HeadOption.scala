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

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.{ Sink, Source }

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
