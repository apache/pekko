/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.Attributes
import pekko.stream.FlowShape
import pekko.stream.Inlet
import pekko.stream.Outlet
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler

/**
 * Concatenating a single element to a stream is common enough that it warrants this optimization
 * which avoids the actual fan-out for such cases.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class SingleConcat[E](singleElem: E) extends GraphStage[FlowShape[E, E]] {

  val in = Inlet[E]("SingleConcat.in")
  val out = Outlet[E]("SingleConcat.out")

  override val shape: FlowShape[E, E] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      override def onPush(): Unit = {
        push(out, grab(in))
      }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit = {
        emit(out, singleElem, () => completeStage())
      }
      setHandlers(in, out, this)
    }

  override def toString: String = s"SingleConcat($singleElem)"
}
