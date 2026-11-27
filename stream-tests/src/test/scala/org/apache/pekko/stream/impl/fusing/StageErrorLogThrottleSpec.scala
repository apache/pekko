/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.impl.fusing

import scala.concurrent.{ Await, Promise }
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
      EventFilter.warning(
        message = "4 additional stage error(s) were suppressed during throttle period",
        occurrences = 1).intercept {
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
    }

    "not let an Off stage consume the error log budget" in {
      val first = Promise[Int]()
      val second = Promise[Int]()
      val off = Attributes.logLevels(onFailure = Attributes.LogLevels.Off)
      EventFilter.warning(start = "1 additional stage error(s)", occurrences = 0).intercept {
        EventFilter[IllegalArgumentException](pattern = "Error in stage.*", occurrences = 1).intercept {
          val (firstDone, secondDone) = RunnableGraph
            .fromGraph(GraphDSL.createGraph(Sink.ignore, Sink.ignore)(Keep.both) { implicit b => (left, right) =>
              import GraphDSL.Implicits._
              Source.future(first.future)  ~> mkFailingStage.withAttributes(off) ~> left
              Source.future(second.future) ~> mkFailingStage                     ~> right
              ClosedShape
            })
            .run()
          first.success(1)
          Await.result(firstDone.failed, 3.seconds).getMessage shouldBe "test failure"
          second.success(2)
          Await.result(secondDone.failed, 3.seconds).getMessage shouldBe "test failure"
        }
      }
    }

    "not summarize failures that were all set to Off" in {
      val off = Attributes.logLevels(onFailure = Attributes.LogLevels.Off)
      EventFilter.warning(pattern = ".*additional stage error.*", occurrences = 0).intercept {
        EventFilter[IllegalArgumentException](pattern = "Error in stage.*", occurrences = 0).intercept {
          val done = Source.single(1)
            .alsoTo(Flow[Int].via(mkFailingStage.withAttributes(off)).to(Sink.ignore))
            .via(mkFailingStage.withAttributes(off))
            .runWith(Sink.ignore)
          Await.result(done.failed, 3.seconds).getMessage shouldBe "test failure"
        }
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

class StageErrorLogThrottleInvalidSpec extends StreamSpec("""
    pekko.stream.materializer.stage-errors-log-throttle-period = "not a duration"
  """) {
  "An invalid error log throttle setting" must {
    "leave failure logging enabled and preserve each original stream failure" in {
      EventFilter[IllegalArgumentException](pattern = "Error in stage.*", occurrences = 2).intercept {
        for (_ <- 1 to 2) {
          val expected = new IllegalArgumentException("original stage error")
          val stage = new SimpleLinearGraphStage[Int] {
            override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
              new GraphStageLogic(shape) with InHandler with OutHandler {
                setHandlers(in, out, this)
                override def onPush(): Unit = throw expected
                override def onPull(): Unit = pull(in)
              }
          }
          val failure = Source.single(1).via(stage).runWith(Sink.ignore)
          (Await.result(failure.failed, 3.seconds) should be).theSameInstanceAs(expected)
        }
      }
    }
  }
}
