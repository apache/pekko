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

package org.apache.pekko.stream.scaladsl

import org.apache.pekko.stream.testkit.StreamSpec

class FlowCollectTypeSpec extends StreamSpec {

  sealed class Fruit
  class Orange extends Fruit
  object Orange extends Orange
  class Apple extends Fruit
  object Apple extends Apple

  "A CollectType" must {

    "collectType with references" in {
      val fruit = Source(List(Orange, Apple, Apple, Orange))

      val apples = fruit.collectType[Apple].runWith(Sink.seq).futureValue
      apples should equal(List(Apple, Apple))
      val oranges = fruit.collectType[Orange].runWith(Sink.seq).futureValue
      oranges should equal(List(Orange, Orange))
      val all = fruit.collectType[Fruit].runWith(Sink.seq).futureValue
      all should equal(List(Orange, Apple, Apple, Orange))
    }

    "collectType with primitives" in {
      val numbers = Source(List[Int](1, 2, 3) ++ List[Double](1.5))

      val integers = numbers.collectType[Int].runWith(Sink.seq).futureValue
      integers should equal(List(1, 2, 3))
      val doubles = numbers.collectType[Double].runWith(Sink.seq).futureValue
      doubles should equal(List(1.5))
      val all = numbers.collectType[Any].runWith(Sink.seq).futureValue
      all should equal(List(1, 2, 3, 1.5))
    }

  }

}
