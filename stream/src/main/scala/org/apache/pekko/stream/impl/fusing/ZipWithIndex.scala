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
import pekko.japi.Pair
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ZipWithIndex extends GraphStage[FlowShape[Any, (Any, Long)]] {
  val in = Inlet[Any]("ZipWithIndex.in")
  val out = Outlet[(Any, Long)]("ZipWithIndex.out")
  override val shape = FlowShape(in, out)
  override def initialAttributes: Attributes = DefaultAttributes.zipWithIndex
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var index = 0L
      override def onPush(): Unit = {
        push(out, (grab(in), index))
        index += 1
      }

      override def onPull(): Unit = pull(in)
      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ZipWithIndexJava extends GraphStage[FlowShape[Any, Pair[Any, Long]]] {
  val in = Inlet[Any]("ZipWithIndex.in")
  val out = Outlet[Pair[Any, Long]]("ZipWithIndex.out")
  override val shape = FlowShape(in, out)
  override def initialAttributes: Attributes = DefaultAttributes.zipWithIndex
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var index = 0L
      override def onPush(): Unit = {
        push(out, new Pair(grab(in), index))
        index += 1
      }

      override def onPull(): Unit = pull(in)
      setHandlers(in, out, this)
    }
}
