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

//#imports
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Source

//#imports
object Fold extends App {

  // #histogram
  case class Histogram(low: Long = 0, high: Long = 0) {
    def add(i: Int): Histogram = if (i < 100) copy(low = low + 1) else copy(high = high + 1)
  }
  // #histogram

  implicit val sys: ActorSystem = ActorSystem()

  // #fold
  Source(1 to 150).fold(Histogram())((acc, n) => acc.add(n)).runForeach(println)

  // Prints: Histogram(99,51)
  // #fold
}
