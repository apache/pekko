/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2013-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.duration

import language.postfixOps

object Scala {
  // #dsl
  import scala.concurrent.duration._

  val fivesec = 5.seconds
  val threemillis = 3.millis
  val diff = fivesec - threemillis
  assert(diff < fivesec)
  val fourmillis = threemillis * 4 / 3 // you cannot write it the other way around
  val n = threemillis / (1.millisecond)
  // #dsl

  // #deadline
  val deadline = 10.seconds.fromNow
  // do something
  val rest = deadline.timeLeft
  // #deadline
}
