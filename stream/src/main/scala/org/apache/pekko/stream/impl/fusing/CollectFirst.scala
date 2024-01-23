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
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.Attributes.SourceLocation
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream._

import scala.annotation.nowarn
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class CollectFirst[In, Out](pf: PartialFunction[In, Out]) extends GraphStage[FlowShape[In, Out]] {
  private val in = Inlet[In]("CollectFirst.in")
  private val out = Outlet[Out]("CollectFirst.out")
  override val shape = FlowShape(in, out)

  override def initialAttributes: Attributes = DefaultAttributes.collectFirst and SourceLocation.forLambda(pf)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      import Collect.NotApplied

      @nowarn("msg=Any")
      override final def onPush(): Unit =
        try {
          // 1. `applyOrElse` is faster than (`pf.isDefinedAt` and then `pf.apply`)
          // 2. using reference comparing here instead of pattern matching can generate less and quicker bytecode,
          //   eg: just a simple `IF_ACMPNE`, and you can find the same trick in `CollectWhile` operator.
          //   If you interest, you can check the associated PR for this change and the
          //   current implementation of `scala.collection.IterableOnceOps.collectFirst`.
          pf.applyOrElse(grab(in), NotApplied) match {
            case _: NotApplied.type => pull(in)
            case elem: Out @unchecked =>
              push(out, elem)
              completeStage()
          }
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case _                =>
                // The !hasBeenPulled(in) check is not required here since it
                // isn't possible to do an additional pull(in) due to the nature
                // of how collect works
                pull(in)
            }
        }

      override final def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }

  override def toString: String = "CollectFirst"
}
