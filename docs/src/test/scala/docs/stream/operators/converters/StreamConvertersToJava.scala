/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.converters

// #import
import java.util.stream
import java.util.stream.IntStream

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.scaladsl.StreamConverters
// #import
import pekko.testkit.PekkoSpec
import org.scalatest.concurrent.Futures

import scala.collection.immutable
import scala.concurrent.Future

class StreamConvertersToJava extends PekkoSpec with Futures {

  "demonstrate materialization to Java8 streams" in {
    // #asJavaStream
    val source: Source[Int, NotUsed] = Source(0 to 9).filter(_ % 2 == 0)

    val sink: Sink[Int, stream.Stream[Int]] = StreamConverters.asJavaStream[Int]()

    val jStream: java.util.stream.Stream[Int] = source.runWith(sink)
    // #asJavaStream
    jStream.count should be(5)
  }

  "demonstrate materialization to Java8 streams with methods on Sink" in {
    // #asJavaStreamOnSink
    val source: Source[Int, NotUsed] = Source(0 to 9).filter(_ % 2 == 0)

    val jStream: java.util.stream.Stream[Int] = source.runWith(Sink.asJavaStream[Int]())
    // #asJavaStreamOnSink
    jStream.count should be(5)
  }

  "demonstrate conversion from Java8 streams" in {
    // #fromJavaStream
    def factory(): IntStream = IntStream.rangeClosed(0, 9)
    val source: Source[Int, NotUsed] = StreamConverters.fromJavaStream(() => factory()).map(_.intValue())
    val sink: Sink[Int, Future[immutable.Seq[Int]]] = Sink.seq[Int]

    val futureInts: Future[immutable.Seq[Int]] = source.toMat(sink)(Keep.right).run()

    // #fromJavaStream
    whenReady(futureInts) { ints =>
      ints should be((0 to 9).toSeq)
    }

  }

}
