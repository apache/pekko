/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.Publisher

import org.apache.pekko.stream.scaladsl.{ Keep, Sink, Source }

class MaybeSourceTest extends PekkoPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] = {
    val (p, pub) = Source.maybe[Int].toMat(Sink.asPublisher(false))(Keep.both).run()
    p.success(Some(1))
    pub
  }

  override def maxElementsFromPublisher(): Long = 1
}
