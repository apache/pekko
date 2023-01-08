/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import scala.concurrent.Await
import scala.concurrent.duration._

import org.reactivestreams.Publisher

import org.apache.pekko
import pekko.stream.impl.EmptyPublisher
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

class GroupByTest extends PekkoPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] =
    if (elements == 0) EmptyPublisher[Int]
    else {
      val futureGroupSource =
        Source(iterable(elements)).groupBy(1, _ => "all").prefixAndTail(0).map(_._2).concatSubstreams.runWith(Sink.head)
      val groupSource = Await.result(futureGroupSource, 3.seconds)
      groupSource.runWith(Sink.asPublisher(false))

    }

}
