/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko
import pekko.stream.{ ActorMaterializer, ActorMaterializerSettings, ClosedShape, Materializer, UniformFanInShape }
import pekko.stream.testkit.{ StreamSpec, TestSubscriber }
import scala.annotation.nowarn

@nowarn // keep unused imports
class FlowZipWithIndexSpec extends StreamSpec {

//#zip-with-index
  import org.apache.pekko
  import pekko.stream.scaladsl.Source
  import pekko.stream.scaladsl.Sink

//#zip-with-index
  val settings = ActorMaterializerSettings(system).withInputBuffer(initialSize = 2, maxSize = 16)

  implicit val materializer: Materializer = ActorMaterializer(settings)

  "A ZipWithIndex for Flow " must {

    "work in the happy case" in {
      val probe = TestSubscriber.manualProbe[(Int, Long)]()
      Source(7 to 10).zipWithIndex.runWith(Sink.fromSubscriber(probe))
      val subscription = probe.expectSubscription()

      subscription.request(2)
      probe.expectNext((7, 0L))
      probe.expectNext((8, 1L))

      subscription.request(1)
      probe.expectNext((9, 2L))
      subscription.request(1)
      probe.expectNext((10, 3L))

      probe.expectComplete()
    }

    "work in fruit example" in {
      // #zip-with-index
      Source(List("apple", "orange", "banana")).zipWithIndex.runWith(Sink.foreach(println))
      // this will print ('apple', 0), ('orange', 1), ('banana', 2)
      // #zip-with-index
    }

    "support junction output ports" in {
      // https://github.com/apache/pekko/issues/1525
      import GraphDSL.Implicits._

      val pickMaxOfThree = GraphDSL.create() { implicit b =>
        val zip1 = b.add(ZipWith[Int, Int, Int](math.max _))
        val zip2 = b.add(ZipWith[Int, Int, Int](math.max _))
        zip1.out ~> zip2.in0

        UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)
      }

      val resultSink = Sink.foreach(println)

      val g = RunnableGraph.fromGraph(GraphDSL.createGraph(resultSink) { implicit b => sink =>
        // importing the partial graph will return its shape (inlets & outlets)
        val pm3 = b.add(pickMaxOfThree)

        Source.single(1)     ~> pm3.in(0)
        Source.single(2)     ~> pm3.in(1)
        Source.single(3)     ~> pm3.in(2)
        pm3.out.zipWithIndex ~> sink.in
        ClosedShape
      })

      g.run()
    }

  }
}
