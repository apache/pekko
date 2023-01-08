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

import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.ExecutionContext

object MapConcat {

  def mapConcat(): Unit = {
    import org.apache.pekko.actor.ActorSystem

    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher

    // #map-concat
    def duplicate(i: Int): List[Int] = List(i, i)

    Source(1 to 3).mapConcat(i => duplicate(i)).runForeach(println)
    // prints:
    // 1
    // 1
    // 2
    // 2
    // 3
    // 3
    // #map-concat

  }

}
