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

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

object Drop {

  implicit val system: ActorSystem = ActorSystem()

  def drop(): Unit = {
    // #drop
    val fiveInts: Source[Int, NotUsed] = Source(1 to 5)
    val droppedThreeInts: Source[Int, NotUsed] = fiveInts.drop(3)

    droppedThreeInts.runForeach(println)
    // 4
    // 5
    // #drop
  }

  def dropWhile(): Unit = {
    // #dropWhile
    val droppedWhileNegative = Source(-3 to 3).dropWhile(_ < 0)

    droppedWhileNegative.runForeach(println)
    // 0
    // 1
    // 2
    // 3
    // #dropWhile
  }

}
