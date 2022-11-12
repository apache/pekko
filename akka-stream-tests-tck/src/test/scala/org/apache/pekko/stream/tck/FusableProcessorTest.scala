/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.Processor

import org.apache.pekko
import pekko.stream._
import pekko.stream.scaladsl.Flow

class FusableProcessorTest extends AkkaIdentityProcessorVerification[Int] {

  override def createIdentityProcessor(maxBufferSize: Int): Processor[Int, Int] = {
    Flow[Int]
      .map(identity)
      .toProcessor
      .withAttributes(Attributes.inputBuffer(initial = maxBufferSize / 2, max = maxBufferSize))
      .run()
  }

  override def createElement(element: Int): Int = element

}
