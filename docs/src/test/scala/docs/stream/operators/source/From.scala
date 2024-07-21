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

package docs.stream.operators.source

import java.util.stream.IntStream

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

object From {

  implicit val system: ActorSystem = null

  def fromIteratorSample(): Unit =
    // #from-iterator
    Source.fromIterator(() => (1 to 3).iterator).runForeach(println)
  // could print
  // 1
  // 2
  // 3
  // #from-iterator

  def fromJavaStreamSample(): Unit =
    // #from-javaStream
    Source.fromJavaStream(() => IntStream.rangeClosed(1, 3)).runForeach(println)
  // could print
  // 1
  // 2
  // 3
  // #from-javaStream

}
