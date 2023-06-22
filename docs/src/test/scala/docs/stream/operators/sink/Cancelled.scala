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

package docs.stream.operators.sink

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ Sink, Source }

import scala.concurrent.ExecutionContextExecutor

object Cancelled {
  implicit val system: ActorSystem = ???
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  def cancelledExample(): NotUsed = {
    // #cancelled
    val source = Source(1 to 5)
    source.runWith(Sink.cancelled)
    // #cancelled
  }
}
