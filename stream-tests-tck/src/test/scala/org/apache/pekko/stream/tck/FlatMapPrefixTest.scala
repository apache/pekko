/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.Publisher

import org.apache.pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }

class FlatMapPrefixTest extends PekkoPublisherVerification[Int] {
  override def createPublisher(elements: Long): Publisher[Int] = {
    val publisher = Source(iterable(elements))
      .map(_.toInt)
      .flatMapPrefixMat(1) { seq =>
        Flow[Int].prepend(Source(seq))
      }(Keep.left)
      .runWith(Sink.asPublisher(false))
    publisher
  }
}
