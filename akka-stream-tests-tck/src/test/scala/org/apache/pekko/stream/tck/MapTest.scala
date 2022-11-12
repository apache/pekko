/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.Processor

import org.apache.pekko.stream.scaladsl.Flow

class MapTest extends AkkaIdentityProcessorVerification[Int] {

  override def createIdentityProcessor(maxBufferSize: Int): Processor[Int, Int] = {
    Flow[Int].map(elem => elem).named("identity").toProcessor.run()
  }

  override def createElement(element: Int): Int = element

}
