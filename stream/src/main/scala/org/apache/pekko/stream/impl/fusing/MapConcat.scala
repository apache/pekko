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

import scala.util.control.Exception.Catcher
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet, Supervision }
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.Attributes.SourceLocation
import pekko.stream.Supervision.Decider
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.ccompat._

/**
 * INTERNAL API
 */
@InternalApi
@ccompatUsedUntil213
private[pekko] final class MapConcat[In, Out](f: In => IterableOnce[Out])
    extends GraphStage[FlowShape[In, Out]] {
  require(f != null, "f function should not be null")
  private val in = Inlet[In]("MapConcat.in")
  private val out = Outlet[Out]("MapConcat.out")
  override val shape: FlowShape[In, Out] = FlowShape(in, out)

  override def initialAttributes: Attributes = DefaultAttributes.mapConcat and SourceLocation.forLambda(f)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private lazy val decider: Decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var currentIterator: Iterator[Out] = _

      private def hasNext = currentIterator != null && currentIterator.hasNext

      override def onPush(): Unit =
        try {
          currentIterator = f(grab(in)).iterator
          tryPushAndPull()
        } catch handleException

      private def tryPushAndPull(): Unit =
        try {
          if (hasNext) {
            push(out, currentIterator.next())
            if (!hasNext && isClosed(in)) {
              completeStage()
            }
          } else if (isClosed(in)) completeStage()
          else pull(in)
        } catch handleException

      private def handleException: Catcher[Unit] = {
        case NonFatal(ex) =>
          decider(ex) match {
            case Supervision.Stop => failStage(ex)
            case _ =>
              if (isClosed(in)) completeStage()
              else if (!hasBeenPulled(in)) pull(in)
          }
      }

      override def onPull(): Unit = tryPushAndPull()

      override def onUpstreamFinish(): Unit = if (!hasNext) completeStage()

      setHandlers(in, out, this)
    }

  override def toString: String = "MapConcat"
}
