/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import scala.concurrent.Await
import scala.concurrent.duration._

import org.reactivestreams.Publisher

import org.apache.pekko
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

class PrefixAndTailTest extends PekkoPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] = {
    val futureTailSource = Source(iterable(elements)).prefixAndTail(0).map { case (_, tail) => tail }.runWith(Sink.head)
    val tailSource = Await.result(futureTailSource, 3.seconds)
    tailSource.runWith(Sink.asPublisher(false))
  }

}
