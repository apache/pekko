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

import org.reactivestreams.Processor

import org.apache.pekko
import pekko.stream.impl.VirtualProcessor
import pekko.stream.scaladsl.Flow

class VirtualProcessorTest extends PekkoIdentityProcessorVerification[Int] {

  override def createIdentityProcessor(maxBufferSize: Int): Processor[Int, Int] = {
    val identity = Flow[Int].map(elem => elem).named("identity").toProcessor.run()
    val left, right = new VirtualProcessor[Int]
    left.subscribe(identity)
    identity.subscribe(right)
    processorFromSubscriberAndPublisher(left, right)
  }

  override def createElement(element: Int): Int = element

}

class VirtualProcessorSingleTest extends PekkoIdentityProcessorVerification[Int] {

  override def createIdentityProcessor(maxBufferSize: Int): Processor[Int, Int] =
    new VirtualProcessor[Int]

  override def createElement(element: Int): Int = element

}
