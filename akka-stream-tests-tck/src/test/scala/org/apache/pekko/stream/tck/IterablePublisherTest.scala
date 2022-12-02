/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams._

import org.apache.pekko
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

class IterablePublisherTest extends PekkoPublisherVerification[Int] {

  override def createPublisher(elements: Long): Publisher[Int] = {
    Source(iterable(elements)).runWith(Sink.asPublisher(false))
  }

}
