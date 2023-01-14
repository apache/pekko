/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

object GroupBy {

  def groupBySourceExample(): Unit = {
    implicit val system: ActorSystem = ???
    // #groupBy
    Source(1 to 10)
      .groupBy(maxSubstreams = 2, _ % 2 == 0) // create two sub-streams with odd and even numbers
      .reduce(_ + _) // for each sub-stream, sum its elements
      .mergeSubstreams // merge back into a stream
      .runForeach(println)
    // 25
    // 30
    // #groupBy
  }

}
