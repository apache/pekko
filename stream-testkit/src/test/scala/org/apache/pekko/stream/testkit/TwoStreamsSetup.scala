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

package org.apache.pekko.stream.testkit

import org.apache.pekko
import pekko.stream._
import pekko.stream.scaladsl._

import org.reactivestreams.Publisher

abstract class TwoStreamsSetup extends BaseTwoStreamsSetup {

  abstract class Fixture {
    def left: Inlet[Int]
    def right: Inlet[Int]
    def out: Outlet[Outputs]
  }

  def fixture(b: GraphDSL.Builder[_]): Fixture

  override def setup(p1: Publisher[Int], p2: Publisher[Int]) = {
    val subscriber = TestSubscriber.probe[Outputs]()
    RunnableGraph
      .fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._
        val f = fixture(b)

        Source.fromPublisher(p1) ~> f.left
        Source.fromPublisher(p2) ~> f.right
        f.out                    ~> Sink.fromSubscriber(subscriber)
        ClosedShape
      })
      .run()

    subscriber
  }

}
