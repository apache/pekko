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

import scala.collection.immutable
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.stream.{ Attributes, Outlet, SourceShape, Supervision }
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.impl.ReactiveStreamsCompliance
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, OutHandler }

private[pekko] final class IterableSource[T](val elements: immutable.Iterable[T]) extends GraphStage[SourceShape[T]] {
  ReactiveStreamsCompliance.requireNonNullElement(elements)

  override protected def initialAttributes: Attributes = DefaultAttributes.iterableSource

  private val out = Outlet[T]("IterableSource.out")
  override val shape: SourceShape[T] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      private var currentIterator: Iterator[T] = _

      override def onPull(): Unit =
        try {
          if (currentIterator eq null) {
            currentIterator = elements.iterator
          }
          tryPushNextOrComplete()
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop   => failStage(ex)
              case Supervision.Resume => tryPushNextOrComplete()
              case Supervision.Restart =>
                currentIterator = elements.iterator
                tryPushNextOrComplete()
            }
        }

      private def tryPushNextOrComplete(): Unit =
        if (currentIterator.hasNext) {
          if (isAvailable(out)) {
            push(out, currentIterator.next())
            if (!currentIterator.hasNext) {
              completeStage()
            }
          }
        } else {
          completeStage()
        }

      setHandler(out, this)
    }

  override def toString: String = "IterableSource"
}
