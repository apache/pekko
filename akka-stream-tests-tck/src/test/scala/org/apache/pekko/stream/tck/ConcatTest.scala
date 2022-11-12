/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.Publisher

import org.apache.pekko
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

class ConcatTest extends AkkaPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] = {
    Source(iterable(elements / 2)).concat(Source(iterable((elements + 1) / 2))).runWith(Sink.asPublisher(false))
  }

}
