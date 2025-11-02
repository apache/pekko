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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet, SubscriptionWithCancelException }
import pekko.stream.Attributes.SourceLocation
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * INTERNAL API
 *
 * @param f The function to be invoked when downstream cancels. The boolean parameter indicates whether the
 *          cancellation was normal (true) or due to failure (false).
 */
@InternalApi private[pekko] final class DoOnCancel[In](
    f: (Throwable, Boolean) => Unit) extends GraphStage[FlowShape[In, In]] {
  require(f != null, "DoOnCancel function must not be null")
  private val in = Inlet[In]("DoOnCancel.in")
  private val out = Outlet[In]("DoOnCancel.out")
  override val shape: FlowShape[In, In] = FlowShape(in, out)

  override def initialAttributes: Attributes = DefaultAttributes.doOnCancel and SourceLocation.forLambda(f)

  override def createLogic(inheritedAttributes: org.apache.pekko.stream.Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      final override def onPush(): Unit = push(out, grab(in))
      final override def onPull(): Unit = pull(in)
      final override def onDownstreamFinish(cause: Throwable): Unit = {
        import SubscriptionWithCancelException._
        val wasCancelledNormally = (cause eq NoMoreElementsNeeded) || (cause eq StageWasCompleted)
        try {
          f(cause, wasCancelledNormally)
        } finally {
          // Ensure the stage is properly finished even if f throws
          super.onDownstreamFinish(cause)
        }
      }

      setHandlers(in, out, this)
    }
}
