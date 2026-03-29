/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.stream.impl.fusing

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream._
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.scaladsl._
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler
import pekko.stream.testkit.StreamSpec
import pekko.testkit.EventFilter

class StageErrorLogThrottleSpec extends StreamSpec("""
    pekko.stream.materializer.stage-errors-log-throttle-period = 2s
    pekko.loglevel = DEBUG
  """) {

  private def mkFailingStage: SimpleLinearGraphStage[Int] = new SimpleLinearGraphStage[Int] {
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        setHandlers(in, out, this)
        override def onPush(): Unit = throw new IllegalArgumentException("test failure")
        override def onPull(): Unit = pull(in)
      }
  }

  "Stage error log throttling" must {

    "suppress repeated errors and flush count on stream finish" in {
      // Use Broadcast to fan-out to 5 independent failing stages sharing one interpreter.
      // Only the first error should be logged; the remaining 4 are suppressed by throttling.
      // The disabled test below verifies all 5 errors log without throttling.
      EventFilter[IllegalArgumentException](pattern = "Error in stage.*", occurrences = 1).intercept {
        val done = RunnableGraph
          .fromGraph(GraphDSL.createGraph(Sink.ignore) { implicit b => sink =>
            import GraphDSL.Implicits._
            val bcast = b.add(Broadcast[Int](5, eagerCancel = false))
            Source.single(1) ~> bcast
            bcast.out(0)     ~> b.add(mkFailingStage) ~> sink
            for (i <- 1 until 5) {
              bcast.out(i) ~> b.add(mkFailingStage) ~> b.add(Sink.ignore)
            }
            ClosedShape
          })
          .run()
        Await.ready(done, 3.seconds)
      }
    }

    "always log a single error even with throttling enabled" in {
      EventFilter[IllegalArgumentException](pattern = "Error in stage.*", occurrences = 1).intercept {
        val result = Source.single(1).via(mkFailingStage).runWith(Sink.ignore)
        Await.ready(result, 3.seconds)
      }
    }
  }
}

class StageErrorLogThrottleDisabledSpec extends StreamSpec("""
    pekko.stream.materializer.stage-errors-log-throttle-period = off
  """) {

  private def mkFailingStage: SimpleLinearGraphStage[Int] = new SimpleLinearGraphStage[Int] {
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        setHandlers(in, out, this)
        override def onPush(): Unit = throw new IllegalArgumentException("test failure")
        override def onPull(): Unit = pull(in)
      }
  }

  "Stage error log throttling when disabled" must {

    "log every error individually without suppression" in {
      // With throttling disabled, each stage error should be logged individually.
      // Using 5 parallel failing stages via Broadcast, all 5 errors should appear.
      EventFilter[IllegalArgumentException](pattern = "Error in stage.*", occurrences = 5).intercept {
        val done = RunnableGraph
          .fromGraph(GraphDSL.createGraph(Sink.ignore) { implicit b => sink =>
            import GraphDSL.Implicits._
            val bcast = b.add(Broadcast[Int](5, eagerCancel = false))
            Source.single(1) ~> bcast
            bcast.out(0)     ~> b.add(mkFailingStage) ~> sink
            for (i <- 1 until 5) {
              bcast.out(i) ~> b.add(mkFailingStage) ~> b.add(Sink.ignore)
            }
            ClosedShape
          })
          .run()
        Await.ready(done, 3.seconds)
      }
    }

    "log single error without suppression warning" in {
      EventFilter[IllegalArgumentException](pattern = "Error in stage.*", occurrences = 1).intercept {
        val result = Source.single(1).via(mkFailingStage).runWith(Sink.ignore)
        Await.ready(result, 3.seconds)
      }
    }
  }
}
