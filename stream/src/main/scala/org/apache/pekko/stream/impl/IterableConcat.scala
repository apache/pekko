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

import scala.util.control.NonFatal

import org.apache.pekko

import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet, Supervision }
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * Concatenating an iterable / iterator / range source to a stream is common enough
 * that it warrants this optimization which avoids the actual fan-out for value-presented
 * sources. Java-stream-backed sources are handled by [[JavaStreamConcat]] because they
 * also need deterministic `BaseStream.close()` on stage termination.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class IterableConcat[E](createIterator: () => Iterator[E]) extends GraphStage[FlowShape[E, E]] {

  val in = Inlet[E]("IterableConcat.in")
  val out = Outlet[E]("IterableConcat.out")

  override val shape: FlowShape[E, E] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      // DO NOT CHANGE
      // WHY: matches `IterableSource` / `IteratorSource` supervision semantics so
      // `Source.iterable / fromIterator(...).withAttributes(supervisionStrategy(...))`
      // behaves identically whether dispatched through this fast path or through the
      // substream-materializing concatGraph path. Without this, a Resume / Restart
      // decider attached to the inlined source would silently degrade to Stop.
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      private var currentIterator: Iterator[E] = _
      private var iterating: Boolean = false

      override def onPush(): Unit = push(out, grab(in))

      override def onPull(): Unit =
        if (!iterating) pull(in)
        else tryPushNextOrComplete()

      override def onUpstreamFinish(): Unit = {
        iterating = true
        if (isAvailable(out)) tryPushNextOrComplete()
      }

      private def tryPushNextOrComplete(): Unit =
        try {
          if (currentIterator eq null) currentIterator = createIterator()
          if (currentIterator.hasNext) {
            if (isAvailable(out)) {
              push(out, currentIterator.next())
              if (!currentIterator.hasNext) completeStage()
            }
          } else {
            completeStage()
          }
        } catch {
          case NonFatal(ex) =>
            if (currentIterator eq null) handleIteratorCreationFailure(ex)
            else handleIteratorFailure(ex)
        }

      private def handleIteratorCreationFailure(ex: Throwable): Unit = decider(ex) match {
        case Supervision.Stop    => failStage(ex)
        case Supervision.Resume  => completeStage()
        case Supervision.Restart =>
          try {
            currentIterator = createIterator()
            tryPushNextOrComplete()
          } catch {
            case NonFatal(restartEx) => failStage(restartEx)
          }
      }

      private def handleIteratorFailure(ex: Throwable): Unit = decider(ex) match {
        case Supervision.Stop    => failStage(ex)
        case Supervision.Resume  => tryPushNextOrComplete()
        case Supervision.Restart =>
          currentIterator = createIterator()
          tryPushNextOrComplete()
      }

      setHandlers(in, out, this)
    }

  override def toString: String = "IterableConcat"
}
