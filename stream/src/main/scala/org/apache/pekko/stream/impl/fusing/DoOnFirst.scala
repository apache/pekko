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
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.Attributes.SourceLocation
import pekko.stream.Supervision
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class DoOnFirst[In](f: In => Unit) extends GraphStage[FlowShape[In, In]] {
  private val in = Inlet[In]("DoOnFirst.in")
  private val out = Outlet[In]("DoOnFirst.out")
  override val shape: FlowShape[In, In] = FlowShape(in, out)

  override def initialAttributes: Attributes = DefaultAttributes.doOnFirst and SourceLocation.forLambda(f)

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      self =>
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      final override def onPush(): Unit = push(out, grab(in))
      final override def onPull(): Unit = pull(in)
      setHandler(out, this)

      // Resume consumes the one-shot and switches to pass-through; Restart keeps this handler armed for retry.
      val firstHandler: InHandler = new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          try f(elem)
          catch {
            case NonFatal(ex) =>
              decider(ex) match {
                case Supervision.Stop =>
                  failStage(ex)
                  return
                case Supervision.Resume =>
                  setHandler(in, self)
                  pull(in)
                  return
                case Supervision.Restart =>
                  pull(in)
                  return
              }
          }
          setHandler(in, self)
          push(out, elem)
        }
      }

      setHandler(in, firstHandler)
    }
}
