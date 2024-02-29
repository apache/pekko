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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ Sink, Source }

import scala.concurrent.{ ExecutionContextExecutor, Future }

object Fold {
  implicit val system: ActorSystem = ???
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  def foldExample(): Future[Unit] = {
    // #fold
    val source = Source(1 to 100)
    val result: Future[Int] = source.runWith(Sink.fold(0)((acc, element) => acc + element))
    result.map(println)
    // 5050
    // #fold
  }
}
