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

object Scan {
  def scanExample(): Unit = {
    import org.apache.pekko.actor.ActorSystem

    implicit val system: ActorSystem = ActorSystem()

    // #scan
    val source = Source(1 to 5)
    source.scan(0)((acc, x) => acc + x).runForeach(println)
    // 0  (= 0)
    // 1  (= 0 + 1)
    // 3  (= 0 + 1 + 2)
    // 6  (= 0 + 1 + 2 + 3)
    // 10 (= 0 + 1 + 2 + 3 + 4)
    // 15 (= 0 + 1 + 2 + 3 + 4 + 5)
    // #scan
  }

}
