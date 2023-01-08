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

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ Flow, Sink, Source }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }

object CompletionTimeout {
  implicit val system: ActorSystem = ???
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  def completionTimeoutExample: Future[Done] = {
    // #completionTimeout
    val source = Source(1 to 10000).map(number => number * number)
    source.completionTimeout(10.milliseconds).run()
    // #completionTimeout
  }
}
