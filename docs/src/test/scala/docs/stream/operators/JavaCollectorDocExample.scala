/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package docs.stream.operators

import java.util.stream.Collectors

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters

object JavaCollectorDocExample {

  // #javaCollector
  val source: Source[String, NotUsed] =
    Source(List("Apache", "Pekko", "Streams"))

  val sink: Sink[String, _] =
    StreamConverters.javaCollector(() => Collectors.toList[String]())
  // #javaCollector
}
