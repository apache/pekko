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

package docs.stream.operators

import org.apache.pekko.actor.ActorSystem

object PartitionDocExample {

  implicit val system: ActorSystem = ???

  // #partition
  import org.apache.pekko
  import pekko.NotUsed
  import pekko.stream.Attributes
  import pekko.stream.Attributes.LogLevels
  import pekko.stream.ClosedShape
  import pekko.stream.scaladsl.Flow
  import pekko.stream.scaladsl.GraphDSL
  import pekko.stream.scaladsl.Partition
  import pekko.stream.scaladsl.RunnableGraph
  import pekko.stream.scaladsl.Sink
  import pekko.stream.scaladsl.Source

  val source: Source[Int, NotUsed] = Source(1 to 10)

  val even: Sink[Int, NotUsed] =
    Flow[Int].log("even").withAttributes(Attributes.logLevels(onElement = LogLevels.Info)).to(Sink.ignore)
  val odd: Sink[Int, NotUsed] =
    Flow[Int].log("odd").withAttributes(Attributes.logLevels(onElement = LogLevels.Info)).to(Sink.ignore)

  RunnableGraph
    .fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val partition = builder.add(Partition[Int](2, element => if (element % 2 == 0) 0 else 1))
      source           ~> partition.in
      partition.out(0) ~> even
      partition.out(1) ~> odd
      ClosedShape
    })
    .run()

  // #partition
}
