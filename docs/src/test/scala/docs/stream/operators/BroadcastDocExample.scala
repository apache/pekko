/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Future

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Broadcast

object BroadcastDocExample {

  implicit val system: ActorSystem = ActorSystem("BroadcastDocExample")

  // #broadcast
  import org.apache.pekko
  import pekko.NotUsed
  import pekko.stream.ClosedShape
  import pekko.stream.scaladsl.GraphDSL
  import pekko.stream.scaladsl.RunnableGraph
  import pekko.stream.scaladsl.Sink
  import pekko.stream.scaladsl.Source

  val source: Source[Int, NotUsed] =
    Source.fromIterator(() => Iterator.continually(ThreadLocalRandom.current().nextInt(100))).take(100)

  val countSink: Sink[Int, Future[Int]] = Sink.fold(0)((acc, elem) => acc + 1)
  val minSink: Sink[Int, Future[Int]] = Sink.fold(0)((acc, elem) => math.min(acc, elem))
  val maxSink: Sink[Int, Future[Int]] = Sink.fold(0)((acc, elem) => math.max(acc, elem))

  val (count: Future[Int], min: Future[Int], max: Future[Int]) =
    RunnableGraph
      .fromGraph(GraphDSL.createGraph(countSink, minSink, maxSink)(Tuple3.apply) {
        implicit builder => (countS, minS, maxS) =>
          import GraphDSL.Implicits._
          val broadcast = builder.add(Broadcast[Int](3))
          source           ~> broadcast
          broadcast.out(0) ~> countS
          broadcast.out(1) ~> minS
          broadcast.out(2) ~> maxS
          ClosedShape
      })
      .run()
  // #broadcast

  // #broadcast-async
  RunnableGraph.fromGraph(GraphDSL.createGraph(countSink.async, minSink.async, maxSink.async)(Tuple3.apply) {
    implicit builder => (countS, minS, maxS) =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](3))
      source           ~> broadcast
      broadcast.out(0) ~> countS
      broadcast.out(1) ~> minS
      broadcast.out(2) ~> maxS
      ClosedShape
  })
  // #broadcast-async
}
