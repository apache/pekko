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

import org.reactivestreams.Publisher

import org.apache.pekko.stream.impl.EmptyPublisher

class EmptyPublisherTest extends PekkoPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] = EmptyPublisher[Int]

  override def maxElementsFromPublisher(): Long = 0
}
