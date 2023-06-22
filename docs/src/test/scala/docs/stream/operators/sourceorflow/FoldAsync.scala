/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

//#imports
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Source

import scala.concurrent.{ ExecutionContext, Future }
//#imports

object FoldAsync extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher

  // #foldAsync
  case class Histogram(low: Long = 0, high: Long = 0) {
    def add(i: Int): Future[Histogram] =
      if (i < 100) Future { copy(low = low + 1) }
      else Future { copy(high = high + 1) }
  }

  Source(1 to 150).foldAsync(Histogram())((acc, n) => acc.add(n)).runForeach(println)

  // Prints: Histogram(99,51)
  // #foldAsync
}
