/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.Publisher

import org.apache.pekko.stream.scaladsl.{ Keep, Sink, Source }

class MaybeSourceTest extends AkkaPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] = {
    val (p, pub) = Source.maybe[Int].toMat(Sink.asPublisher(false))(Keep.both).run()
    p.success(Some(1))
    pub
  }

  override def maxElementsFromPublisher(): Long = 1
}
