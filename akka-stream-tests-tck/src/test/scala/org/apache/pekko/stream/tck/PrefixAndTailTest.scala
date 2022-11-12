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

class PrefixAndTailTest extends AkkaPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] = {
    val futureTailSource = Source(iterable(elements)).prefixAndTail(0).map { case (_, tail) => tail }.runWith(Sink.head)
    val tailSource = Await.result(futureTailSource, 3.seconds)
    tailSource.runWith(Sink.asPublisher(false))
  }

}
