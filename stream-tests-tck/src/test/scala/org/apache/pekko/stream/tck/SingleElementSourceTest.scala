/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.Publisher

import org.apache.pekko
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

class SingleElementSourceTest extends PekkoPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] =
    Source.single(1).runWith(Sink.asPublisher(false))

  override def maxElementsFromPublisher(): Long = 1
}
