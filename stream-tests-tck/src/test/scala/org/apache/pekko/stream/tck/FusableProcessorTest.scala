/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.apache.pekko
import pekko.stream._
import pekko.stream.scaladsl.Flow

import org.reactivestreams.Processor

class FusableProcessorTest extends PekkoIdentityProcessorVerification[Int] {

  override def createIdentityProcessor(maxBufferSize: Int): Processor[Int, Int] = {
    Flow[Int]
      .map(identity)
      .toProcessor
      .withAttributes(Attributes.inputBuffer(initial = maxBufferSize / 2, max = maxBufferSize))
      .run()
  }

  override def createElement(element: Int): Int = element

}
