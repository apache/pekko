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

import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, Outlet, SourceShape, Supervision }
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.impl.fusing.GraphStages.ValuePresentedSource
import pekko.stream.stage.{ GraphStage, GraphStageLogic, OutHandler }

@InternalApi
private[pekko] final class IteratorSource[T](
    val createIterator: () => Iterator[T],
    defaultAttributes: Attributes)
    extends GraphStage[SourceShape[T]]
    with ValuePresentedSource {

  override protected def initialAttributes: Attributes = defaultAttributes

  private val out = Outlet[T]("IteratorSource.out")
  override val shape: SourceShape[T] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      private var currentIterator: Iterator[T] = _

      override def onPull(): Unit =
        try {
          if (currentIterator eq null) {
            currentIterator = createIterator()
          }
          pushNextOrComplete()
        } catch {
          case NonFatal(ex) =>
            if (currentIterator eq null) handleIteratorCreationFailure(ex)
            else handleIteratorFailure(ex)
        }

      private def handleIteratorCreationFailure(ex: Throwable): Unit =
        decider(ex) match {
          case Supervision.Stop   => failStage(ex)
          case Supervision.Resume =>
            completeStage()
          case Supervision.Restart =>
            try {
              currentIterator = createIterator()
              pushNextOrComplete()
            } catch {
              case NonFatal(restartEx) => failStage(restartEx)
            }
        }

      private def handleIteratorFailure(ex: Throwable): Unit =
        decider(ex) match {
          case Supervision.Stop    => failStage(ex)
          case Supervision.Resume  => pushNextOrComplete()
          case Supervision.Restart =>
            currentIterator = createIterator()
            pushNextOrComplete()
        }

      private def pushNextOrComplete(): Unit =
        if (currentIterator.hasNext) {
          if (isAvailable(out)) {
            push(out, currentIterator.next())
            if (!currentIterator.hasNext)
              completeStage()
          }
        } else {
          completeStage()
        }

      setHandler(out, this)
    }

  override def toString: String = "IteratorSource"
}
