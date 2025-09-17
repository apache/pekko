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

package docs.stream.operators.sourceorflow

import scala.concurrent.duration._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.ThrottleMode
import pekko.stream.scaladsl.{ Sink, Source }

object Throttle extends App {

  implicit val sys: ActorSystem = ActorSystem("25fps-stream")

  val frameSource: Source[Int, NotUsed] =
    Source.fromIterator(() => Iterator.from(0))

  // #throttle
  val framesPerSecond = 24

  // val frameSource: Source[Frame,_]
  val videoThrottling = frameSource.throttle(framesPerSecond, 1.second)
  // serialize `Frame` and send over the network.
  // #throttle

  // #throttle-with-burst
  // val frameSource: Source[Frame,_]
  val videoThrottlingWithBurst = frameSource.throttle(
    framesPerSecond,
    1.second,
    framesPerSecond * 30, // maximumBurst
    ThrottleMode.Shaping)
  // serialize `Frame` and send over the network.
  // #throttle-with-burst

  videoThrottling.take(1000).to(Sink.foreach(println)).run()
  videoThrottlingWithBurst.take(1000).to(Sink.foreach(println)).run()
}

object ThrottleCommon {

  // used in ThrottleJava
  case class Frame(i: Int)

}
