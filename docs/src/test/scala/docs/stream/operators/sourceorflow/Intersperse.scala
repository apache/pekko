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

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

object Intersperse extends App {
  import org.apache.pekko.actor.ActorSystem

  implicit val system: ActorSystem = ActorSystem()

  // #intersperse
  Source(1 to 4).map(_.toString).intersperse("[", ", ", "]").runWith(Sink.foreach(print))
  // prints
  // [1, 2, 3, 4]
  // #intersperse

  system.terminate()
}
