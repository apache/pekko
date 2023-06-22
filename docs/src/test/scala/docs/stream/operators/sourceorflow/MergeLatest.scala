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

package docs.stream.operators.sourceorflow
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

object MergeLatest extends App {
  implicit val system: ActorSystem = ActorSystem()

  // #mergeLatest
  val prices = Source(List(100, 101, 99, 103))
  val quantity = Source(List(1, 3, 4, 2))

  prices
    .mergeLatest(quantity)
    .map {
      case price :: quantity :: Nil => price * quantity
    }
    .runForeach(println)

  // prints something like:
  // 100
  // 101
  // 303
  // 297
  // 396
  // 412
  // 206
  // #mergeLatest
}
