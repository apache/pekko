/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko
import pekko.stream._
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/** INTERNAL API */
private[stream] final class CoupledTerminationBidi[I, O] extends GraphStage[BidiShape[I, I, O, O]] {
  val in1: Inlet[I] = Inlet("CoupledCompletion.in1")
  val out1: Outlet[I] = Outlet("CoupledCompletion.out1")
  val in2: Inlet[O] = Inlet("CoupledCompletion.in2")
  val out2: Outlet[O] = Outlet("CoupledCompletion.out2")
  override val shape: BidiShape[I, I, O, O] = BidiShape(in1, out1, in2, out2)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    val handler1 = new InHandler with OutHandler {
      override def onPush(): Unit = push(out1, grab(in1))
      override def onPull(): Unit = pull(in1)

      override def onDownstreamFinish(cause: Throwable): Unit = cancelStage(cause)
      override def onUpstreamFinish(): Unit = completeStage()
      override def onUpstreamFailure(ex: Throwable): Unit = failStage(ex)
    }

    val handler2 = new InHandler with OutHandler {
      override def onPush(): Unit = push(out2, grab(in2))
      override def onPull(): Unit = pull(in2)

      override def onDownstreamFinish(cause: Throwable): Unit = cancelStage(cause)
      override def onUpstreamFinish(): Unit = completeStage()
      override def onUpstreamFailure(ex: Throwable): Unit = failStage(ex)
    }

    setHandlers(in1, out1, handler1)
    setHandlers(in2, out2, handler2)
  }
}
