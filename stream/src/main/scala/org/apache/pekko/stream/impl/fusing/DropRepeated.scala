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
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet, Supervision }
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.OptionVal

private[pekko] final class DropRepeated[T](predicate: (T, T) => Boolean) extends GraphStage[FlowShape[T, T]] {
  require(predicate != null, "predicate function should not be null")

  private val in = Inlet[T]("DropRepeated.in")
  private val out = Outlet[T]("DropRepeated.out")
  override val shape: FlowShape[T, T] = FlowShape(in, out)

  override def initialAttributes: Attributes = DefaultAttributes.dropRepeated

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      private var last: OptionVal[T] = OptionVal.none
      final override def onPush(): Unit = {
        val elem = grab(in)
        last match {
          case OptionVal.Some(lastElem) =>
            try {
              if (predicate(lastElem, elem)) {
                pullOrComplete()
              } else {
                push(out, elem)
              }
              last = OptionVal.Some(elem)
            } catch {
              case NonFatal(e) =>
                decider(e) match {
                  case Supervision.Stop =>
                    failStage(e)
                  case Supervision.Resume =>
                    pullOrComplete()
                  case Supervision.Restart =>
                    last = OptionVal.none
                    pullOrComplete()
                }
            }
          case OptionVal.None =>
            last = OptionVal.Some(elem)
            push(out, last.get)
        }
      }

      private def pullOrComplete(): Unit = if (isClosed(in)) completeStage() else pull(in)

      final override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }
}
