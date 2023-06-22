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

object Grouped {
  def groupedExample(): Unit = {
    import org.apache.pekko.actor.ActorSystem

    implicit val system: ActorSystem = ActorSystem()

    // #grouped
    Source(1 to 7).grouped(3).runForeach(println)
    // Vector(1, 2, 3)
    // Vector(4, 5, 6)
    // Vector(7)

    Source(1 to 7).grouped(3).map(_.sum).runForeach(println)
    // 6   (= 1 + 2 + 3)
    // 15  (= 4 + 5 + 6)
    // 7   (= 7)
    // #grouped
  }

}
