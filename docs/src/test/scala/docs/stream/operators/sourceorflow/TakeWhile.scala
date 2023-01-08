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

object TakeWhile {
  def takeWhileExample(): Unit = {
    import org.apache.pekko.actor.ActorSystem
    import org.apache.pekko.stream.scaladsl.Source

    implicit val system: ActorSystem = ActorSystem()

    // #take-while
    Source(1 to 10).takeWhile(_ < 3).runForeach(println)
    // prints
    // 1
    // 2
    // #take-while
  }
}
