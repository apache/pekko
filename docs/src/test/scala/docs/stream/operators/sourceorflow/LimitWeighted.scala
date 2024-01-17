/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

object LimitWeighted {

  implicit val system: ActorSystem[?] = ???

  def simple(): Unit = {
    // #simple
    val untrustedSource: Source[ByteString, NotUsed] = Source.repeat(ByteString("element"))

    val allBytes: Future[ByteString] =
      untrustedSource.limitWeighted(max = 10000)(_.length).runReduce(_ ++ _)
    // #simple
  }

}
