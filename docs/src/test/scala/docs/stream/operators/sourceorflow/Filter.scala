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

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

object Filter {

  implicit val system: ActorSystem = ActorSystem()

  def filterExample(): Unit = {
    // #filter
    val words: Source[String, NotUsed] =
      Source(
        ("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt " +
        "ut labore et dolore magna aliqua").split(" ").toList)

    val longWords: Source[String, NotUsed] = words.filter(_.length > 6)

    longWords.runForeach(println)
    // consectetur
    // adipiscing
    // eiusmod
    // incididunt
    // #filter
  }

  def filterNotExample(): Unit = {
    // #filterNot
    val words: Source[String, NotUsed] =
      Source(
        ("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt " +
        "ut labore et dolore magna aliqua").split(" ").toList)

    val longWords: Source[String, NotUsed] = words.filterNot(_.length <= 6)

    longWords.runForeach(println)
    // consectetur
    // adipiscing
    // eiusmod
    // incididunt
    // #filterNot
  }
}
