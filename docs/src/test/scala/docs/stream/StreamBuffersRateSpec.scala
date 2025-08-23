/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream

import org.apache.pekko.NotUsed
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.testkit.PekkoSpec

class StreamBuffersRateSpec extends PekkoSpec {

  "Demonstrate pipelining" in {
    def println(s: Any) = ()
    // #pipelining
    Source(1 to 3)
      .map { i =>
        println(s"A: $i"); i
      }
      .async
      .map { i =>
        println(s"B: $i"); i
      }
      .async
      .map { i =>
        println(s"C: $i"); i
      }
      .async
      .runWith(Sink.ignore)
    // #pipelining
  }

  "Demonstrate buffer sizes" in {
    // #section-buffer
    val section = Flow[Int].map(_ * 2).async.addAttributes(Attributes.inputBuffer(initial = 1, max = 1)) // the buffer size of this map is 1
    val flow = section.via(Flow[Int].map(_ / 2)).async // the buffer size of this map is the default
    val runnableGraph =
      Source(1 to 10).via(flow).to(Sink.foreach(elem => println(elem)))

    val withOverriddenDefaults = runnableGraph.withAttributes(Attributes.inputBuffer(initial = 64, max = 64))
    // #section-buffer
  }

  "buffering abstraction leak" in {
    // #buffering-abstraction-leak
    import scala.concurrent.duration._
    case class Tick()

    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // this is the asynchronous stage in this graph
      val zipper = b.add(ZipWith[Tick, Int, Int]((tick, count) => count).async)

      Source.tick(initialDelay = 3.second, interval = 3.second, Tick()) ~> zipper.in0

      Source
        .tick(initialDelay = 1.second, interval = 1.second, "message!")
        .conflateWithSeed(seed = _ => 1)((count, _) => count + 1) ~> zipper.in1

      zipper.out ~> Sink.foreach(println)
      ClosedShape
    })
    // #buffering-abstraction-leak
  }

  "explicit buffers" in {
    trait Job
    def inboundJobsConnector(): Source[Job, NotUsed] = Source.empty
    // #explicit-buffers-backpressure
    // Getting a stream of jobs from an imaginary external system as a Source
    val jobs: Source[Job, NotUsed] = inboundJobsConnector()
    jobs.buffer(1000, OverflowStrategy.backpressure)
    // #explicit-buffers-backpressure

    // #explicit-buffers-droptail
    jobs.buffer(1000, OverflowStrategy.dropTail)
    // #explicit-buffers-droptail

    // #explicit-buffers-drophead
    jobs.buffer(1000, OverflowStrategy.dropHead)
    // #explicit-buffers-drophead

    // #explicit-buffers-dropbuffer
    jobs.buffer(1000, OverflowStrategy.dropBuffer)
    // #explicit-buffers-dropbuffer

    // #explicit-buffers-fail
    jobs.buffer(1000, OverflowStrategy.fail)
    // #explicit-buffers-fail

  }

}
