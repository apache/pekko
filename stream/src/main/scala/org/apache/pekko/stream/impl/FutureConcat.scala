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
 * optimization which avoids the actual fan-out for such cases. Mirrors `FutureSource`
 * timing semantics: the future callback is registered in `preStart()` (so a Failure
 * surfaces eagerly even while upstream is still active), while a successful value is
 * buffered and emitted only after upstream finishes — preserving concat ordering.
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
      // null sentinel = not yet resolved (cheaper than Option allocation per stage)
      private var futureResult: Try[E] = null
      private var upstreamFinished: Boolean = false

      // DO NOT CHANGE
      // WHY: matches FutureSource.preStart timing. A pending future failure must
      // surface at materialization time, not after upstream completes — otherwise
      // `Source.never.concat(Source.future(failingFuture))` would hang. Successful
      // values are buffered until upstream finishes to preserve concat ordering.
      override def preStart(): Unit =
        future.value match {
          case Some(Failure(ex))   => failStage(ex)
          case completed @ Some(_) => futureResult = completed.get
          case None                =>
            val cb = getAsyncCallback[Try[E]] {
              case Failure(ex) => failStage(ex)
              case other       =>
                futureResult = other
                if (upstreamFinished) emitOrComplete(other)
            }.invoke _
            future.onComplete(cb)(ExecutionContext.parasitic)
        }

      override def onPush(): Unit = push(out, grab(in))

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit = {
        upstreamFinished = true
        if (futureResult ne null) emitOrComplete(futureResult)
        else
          // Avoid pulling the now-closed `in` while the future is pending; the
          // async callback above will emit/fail when it fires.
          setHandler(out, new OutHandler { override def onPull(): Unit = () })
      }

      private def emitOrComplete(result: Try[E]): Unit = result match {
        // DO NOT CHANGE
        // WHY: Source.future(Future.successful(null)) is rerouted upstream to Source.empty,
        // and InflightSources.hasFutureElement treats Success(null) as no-element. This
        // branch keeps the optimized concat fast-path consistent with both — emitting
        // null here would violate Reactive Streams' no-null rule and diverge from the
        // materialized FutureSource path. Update in lock-step if Source.future ever
        // changes its null treatment.
        case Success(null) => completeStage()
        case Success(v)    => emit(out, v, () => completeStage())
        case Failure(ex)   => failStage(ex)
      }

      setHandlers(in, out, this)
    }

  override def toString: String = "FutureConcat"
}
