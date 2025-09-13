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

object Collection {
  implicit val system: ActorSystem = ???
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  def collectionExample(): Unit = {
    // #collection
    val source = Source(1 to 5)
    val result: Future[List[Int]] = source.runWith(Sink.collection[Int, List[Int]])
    result.foreach(println)
    // List(1, 2, 3, 4, 5)
    // #collection
  }
}
