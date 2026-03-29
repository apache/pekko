/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.flow

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ GatherCollector, Gatherer, Source }

object Gather {

  implicit val actorSystem: ActorSystem = ???

  def zipWithIndex(): Unit = {
    // #zipWithIndex
    Source(List("A", "B", "C", "D"))
      .gather(() => {
        var index = 0L
        (elem: String, collector: GatherCollector[(String, Long)]) => {
          collector.push((elem, index))
          index += 1
        }
      })
      .runForeach(println)
    // prints
    // (A,0)
    // (B,1)
    // (C,2)
    // (D,3)
    // #zipWithIndex
  }

  def grouped(): Unit = {
    // #grouped
    Source(1 to 10)
      .gather(() =>
        new Gatherer[Int, List[Int]] {
          private var buffer = List.empty[Int]

          override def apply(elem: Int, collector: GatherCollector[List[Int]]): Unit = {
            buffer = elem :: buffer
            if (buffer.size == 3) {
              collector.push(buffer.reverse)
              buffer = Nil
            }
          }

          override def onComplete(collector: GatherCollector[List[Int]]): Unit =
            if (buffer.nonEmpty)
              collector.push(buffer.reverse)
        })
      .runForeach(println)
    // prints
    // List(1, 2, 3)
    // List(4, 5, 6)
    // List(7, 8, 9)
    // List(10)
    // #grouped
  }
}
