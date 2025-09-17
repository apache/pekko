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

package org.apache.pekko.stream.impl.fusing

import org.apache.pekko
import pekko.stream._
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.testkit.Utils.TE
import pekko.testkit.PekkoSpec

class ChasingEventsSpec extends PekkoSpec("""
    pekko.stream.materializer.debug.fuzzing-mode = off
  """) {

  class CancelInChasedPull extends GraphStage[FlowShape[Int, Int]] {
    val in = Inlet[Int]("Propagate.in")
    val out = Outlet[Int]("Propagate.out")
    override val shape: FlowShape[Int, Int] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        private var first = true
        override def onPush(): Unit = push(out, grab(in))
        override def onPull(): Unit = {
          pull(in)
          if (!first) cancel(in)
          first = false
        }

        setHandlers(in, out, this)
      }
  }

  class CompleteInChasedPush extends GraphStage[FlowShape[Int, Int]] {
    val in = Inlet[Int]("Propagate.in")
    val out = Outlet[Int]("Propagate.out")
    override val shape: FlowShape[Int, Int] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        override def onPush(): Unit = {
          push(out, grab(in))
          complete(out)
        }
        override def onPull(): Unit = pull(in)

        setHandlers(in, out, this)
      }
  }

  class FailureInChasedPush extends GraphStage[FlowShape[Int, Int]] {
    val in = Inlet[Int]("Propagate.in")
    val out = Outlet[Int]("Propagate.out")
    override val shape: FlowShape[Int, Int] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        override def onPush(): Unit = {
          push(out, grab(in))
          fail(out, TE("test failure"))
        }
        override def onPull(): Unit = pull(in)

        setHandlers(in, out, this)
      }
  }

  class ChasableSink extends GraphStage[SinkShape[Int]] {
    val in = Inlet[Int]("Chaseable.in")
    override val shape: SinkShape[Int] = SinkShape(in)

    @throws(classOf[Exception])
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler {
        override def preStart(): Unit = pull(in)
        override def onPush(): Unit = pull(in)
        setHandler(in, this)
      }
  }

  "Event chasing" must {

    "propagate cancel if enqueued immediately after pull" in {
      val upstream = TestPublisher.probe[Int]()

      Source.fromPublisher(upstream).via(new CancelInChasedPull).runWith(Sink.ignore)

      upstream.sendNext(0)
      upstream.expectCancellation()

    }

    "propagate complete if enqueued immediately after push" in {
      val downstream = TestSubscriber.probe[Int]()

      Source(1 to 10).via(new CompleteInChasedPush).runWith(Sink.fromSubscriber(downstream))

      downstream.requestNext(1)
      downstream.expectComplete()

    }

    "propagate failure if enqueued immediately after push" in {
      val downstream = TestSubscriber.probe[Int]()

      Source(1 to 10).via(new FailureInChasedPush).runWith(Sink.fromSubscriber(downstream))

      downstream.requestNext(1)
      downstream.expectError()

    }

  }

}
