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

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * Concatenating a `Source.future` to a stream is common enough that it warrants this
 * optimization which avoids the actual fan-out for such cases. After upstream
 * finishes, the stage emits the future's resolved value (or fails) without
 * paying for substream materialization. Pending futures register an async callback
 * that fires once the future completes.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class FutureConcat[E](future: Future[E]) extends GraphStage[FlowShape[E, E]] {

  val in = Inlet[E]("FutureConcat.in")
  val out = Outlet[E]("FutureConcat.out")

  override val shape: FlowShape[E, E] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      override def onPush(): Unit = push(out, grab(in))

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit =
        future.value match {
          case Some(completed) => handle(completed)
          case None            =>
            // Avoid pulling the now-closed `in` while the future is pending.
            setHandler(out,
              new OutHandler {
                override def onPull(): Unit = () // wait for the async callback
              })
            val cb = getAsyncCallback[Try[E]](handle).invoke _
            future.onComplete(cb)(ExecutionContext.parasitic)
        }

      private def handle(result: Try[E]): Unit = result match {
        case Success(null) => completeStage()
        case Success(v)    => emit(out, v, () => completeStage())
        case Failure(ex)   => failStage(ex)
      }

      setHandlers(in, out, this)
    }

  override def toString: String = "FutureConcat"
}
