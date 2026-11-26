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

package org.apache.pekko.stream.impl.fusing

import scala.concurrent.Future
import scala.util.Failure

import org.apache.pekko
import pekko.Done
import pekko.event.Logging
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet, StreamDetachedException }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils.TE
import pekko.testkit.EventFilter

class GraphInterpreterFailureModesSpec extends StreamSpec with GraphInterpreterSpecKit {

  "GraphInterpreter" must {

    "handle failure on onPull" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.pull()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(
        Set(Cancel(upstream, testException), OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onPush" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.pull()
      stepAll()
      clearEvents()
      upstream.push(0)
      failOnNextEvent()
      stepAll()

      lastEvents() should be(
        Set(Cancel(upstream, testException), OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onPull while cancel is pending" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.pull()
      downstream.cancel()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(Cancel(upstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onPush while complete is pending" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.pull()
      stepAll()
      clearEvents()
      upstream.push(0)
      upstream.complete()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onUpstreamFinish" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      upstream.complete()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onUpstreamFailure" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      upstream.fail(TE("another exception")) // this is not the exception that will be propagated
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onDownstreamFinish" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.cancel()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(Cancel(upstream, testException), PostStop(insideOutStage)))
    }

    "handle failure in preStart" in new FailingStageSetup(initFailOnNextEvent = true) {
      stepAll()

      lastEvents() should be(
        Set(Cancel(upstream, testException), OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure in postStop" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      upstream.complete()
      downstream.cancel()
      failOnPostStop()

      EventFilter.error("Error during postStop in [stage]").intercept {
        stepAll()
        lastEvents() should be(Set.empty)
      }

    }

    "detach pending callback feedback when postStop throws after normal completion" in new TestSetup {
      val thrown = TE("postStop failed")
      var registeredFeedback = Option.empty[Future[Done]]
      var postStops = 0
      var afterPostStops = 0
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")

      val stage: GraphStage[FlowShape[Int, Int]] = new GraphStage[FlowShape[Int, Int]] {
        val in = Inlet[Int]("in")
        val out = Outlet[Int]("out")
        override val shape = FlowShape(in, out)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler with OutHandler {
            setHandlers(in, out, this)
            override def onPush(): Unit = push(out, grab(in))
            override def onPull(): Unit = pull(in)

            private val callback = getAsyncCallback[Unit](_ => ())

            override def preStart(): Unit = {
              registeredFeedback = Some(callback.invokeWithFeedback(()))
            }

            override def postStop(): Unit = {
              postStops += 1
              throw thrown
            }

            override protected[stream] def afterPostStop(): Unit = {
              afterPostStops += 1
              super.afterPostStop()
            }
          }
      }
      builder(stage).connect(source, stage.shape.in).connect(stage.shape.out, sink).init()

      // The manually stepped interpreter queues no async input, so feedback stays pending until teardown.
      val feedback = registeredFeedback.getOrElse(fail("preStart did not register feedback"))
      feedback.isCompleted shouldBe false
      EventFilter.custom({
          case error: Logging.Error =>
            (error.cause eq thrown) && error.message.toString.startsWith("Error during postStop in [")
        }, occurrences = 1).intercept {
        source.onComplete()
      }

      lastEvents() shouldBe Set(OnComplete(sink))
      postStops shouldBe 1
      feedback.value should matchPattern { case Some(Failure(_: StreamDetachedException)) => }
      afterPostStops shouldBe 1
    }

    "log cleanup failure separately after normal completion" in new TestSetup {
      val thrown = TE("cleanup failed")
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")

      val stage: GraphStage[FlowShape[Int, Int]] = new GraphStage[FlowShape[Int, Int]] {
        val in = Inlet[Int]("in")
        val out = Outlet[Int]("out")
        override val shape = FlowShape(in, out)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler with OutHandler {
            setHandlers(in, out, this)
            override def onPush(): Unit = push(out, grab(in))
            override def onPull(): Unit = pull(in)

            override protected[stream] def afterPostStop(): Unit = {
              super.afterPostStop()
              throw thrown
            }
          }
      }
      builder(stage).connect(source, stage.shape.in).connect(stage.shape.out, sink).init()

      EventFilter.custom({
          case error: Logging.Error =>
            (error.cause eq thrown) && error.message.toString.startsWith("Error during cleanup after postStop in [")
        }, occurrences = 1).intercept {
        source.onComplete()
      }

      lastEvents() shouldBe Set(OnComplete(sink))
    }
  }

}
