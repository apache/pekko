/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.apache.pekko.testkit.PekkoSpec

import scala.collection.immutable

class FlowBufferUntilChangedSpec extends PekkoSpec {

  "bufferUntilChanged" should {
    "buffer elements until they change" in {
      val sink = TestSink.probe[immutable.Seq[String]]
      Source("A" :: "B" :: "B" :: "C" :: "C" :: "C" :: "D" :: Nil)
        .bufferUntilChanged
        .runWith(sink)
        .request(4)
        .expectNext(Seq("A"))
        .expectNext(Seq("B", "B"))
        .expectNext(Seq("C", "C", "C"))
        .expectNext(Seq("D"))
        .expectComplete()
    }

    "buffer elements until they change with keySelector" in {
      val sink = TestSink.probe[immutable.Seq[Int]]
      Source(1 :: 2 :: 3 :: 13 :: 4 :: 14 :: Nil)
        .bufferUntilChanged(_ % 10)
        .runWith(sink)
        .request(4)
        .expectNext(Seq(1))
        .expectNext(Seq(2))
        .expectNext(Seq(3, 13))
        .expectNext(Seq(4, 14))
        .expectComplete()
    }

    "buffer elements until they change with keySelector and keyComparator" in {
      val sink = TestSink.probe[immutable.Seq[Int]]
      Source(1 :: 3 :: 5 :: 2 :: 4 :: 6 :: Nil)
        .bufferUntilChanged(identity, (a, b) => (a % 2) == (b % 2))
        .runWith(sink)
        .request(2)
        .expectNext(Seq(1, 3, 5))
        .expectNext(Seq(2, 4, 6))
        .expectComplete()
    }

    "work with empty sources" in {
      val sink = TestSink.probe[immutable.Seq[String]]
      Source.empty[String]
        .bufferUntilChanged
        .runWith(sink)
        .request(1)
        .expectComplete()
    }

    "work with single element sources" in {
      val sink = TestSink.probe[immutable.Seq[String]]
      Source.single("A")
        .bufferUntilChanged
        .runWith(sink)
        .request(1)
        .expectNext(Seq("A"))
        .expectComplete()
    }
  }
}
