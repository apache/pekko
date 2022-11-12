/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier

import org.apache.pekko
import pekko.stream.Attributes
import pekko.stream.Inlet
import pekko.stream.SinkShape
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler

class LatchSink(countDownAfter: Int, latch: CountDownLatch) extends GraphStage[SinkShape[Any]] {
  val in: Inlet[Any] = Inlet("LatchSink")
  override val shape: SinkShape[Any] = SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler {

      var n = 0

      override def preStart(): Unit = pull(in)

      override def onUpstreamFailure(ex: Throwable): Unit = {
        println(ex.getMessage)
        ex.printStackTrace()
      }

      override def onPush(): Unit = {
        n += 1
        if (n == countDownAfter)
          latch.countDown()
        grab(in)
        pull(in)
      }

      setHandler(in, this)
    }
}

class BarrierSink(countDownAfter: Int, latch: CountDownLatch, barrierAfter: Int, barrier: CyclicBarrier)
    extends GraphStage[SinkShape[Any]] {
  val in: Inlet[Any] = Inlet("BarrierSink")
  override val shape: SinkShape[Any] = SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler {

      var n = 0

      override def preStart(): Unit = pull(in)

      override def onPush(): Unit = {
        n += 1
        grab(in)
        if (n == countDownAfter)
          latch.countDown()
        else if (n % barrierAfter == 0)
          barrier.await()
        pull(in)
      }

      setHandler(in, this)
    }
}
