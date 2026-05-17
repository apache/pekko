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

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * Concatenating a `Source.failed` to a stream is common enough that it warrants this
 * optimization which avoids the actual fan-out for such cases. After upstream
 * finishes, the stage fails with the captured cause.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class FailedConcat[E](failure: Throwable) extends GraphStage[FlowShape[E, E]] {

  val in = Inlet[E]("FailedConcat.in")
  val out = Outlet[E]("FailedConcat.out")

  override val shape: FlowShape[E, E] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      override def onPush(): Unit = push(out, grab(in))

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit = failStage(failure)

      setHandlers(in, out, this)
    }

  override def toString: String = s"FailedConcat($failure)"
}
