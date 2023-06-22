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

import org.reactivestreams.Publisher

import org.apache.pekko
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.util.ConstantFun

class FlattenTest extends PekkoPublisherVerification[Int] {

  def createPublisher(elements: Long): Publisher[Int] = {
    val s1 = Source(iterable(elements / 2))
    val s2 = Source(iterable((elements + 1) / 2))
    Source(List(s1, s2)).flatMapConcat(ConstantFun.scalaIdentityFunction).runWith(Sink.asPublisher(false))
  }

}
