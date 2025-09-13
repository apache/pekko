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

import org.apache.pekko.stream.scaladsl.Source

object GroupedAdjacentBy {
  def groupedAdjacentByExample(): Unit = {
    import org.apache.pekko.actor.ActorSystem
    implicit val system: ActorSystem = ActorSystem()

    // #groupedAdjacentBy
    Source(List("Hello", "Hi", "Greetings", "Hey"))
      .groupedAdjacentBy(_.head)
      .runForeach(println)
    // prints:
    // Vector(Hello, Hi)
    // Vector(Greetings)
    // Vector(Hey)
    // #groupedAdjacentBy
  }

  def groupedAdjacentByWeightedExample(): Unit = {
    import org.apache.pekko.actor.ActorSystem
    implicit val system: ActorSystem = ActorSystem()

    // #groupedAdjacentByWeighted
    Source(List("Hello", "HiHi", "Hi", "Hi", "Greetings", "Hey"))
      .groupedAdjacentByWeighted(_.head, 4)(_.length)
      .runForeach(println)
    // prints:
    // Vector(Hello)
    // Vector(HiHi)
    // Vector(Hi, Hi)
    // Vector(Greetings)
    // Vector(Hey)
    // #groupedAdjacentByWeighted
  }

}
