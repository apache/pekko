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
import pekko.stream.{ Attributes, Outlet, SourceShape }
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, OutHandler }

@InternalApi
private[pekko] final class ArraySource[T](elements: Array[T]) extends GraphStage[SourceShape[T]] {
  require(elements ne null, "array must not be null")
  override protected def initialAttributes: Attributes = DefaultAttributes.arraySource
  private val out = Outlet[T]("ArraySource.out")
  override val shape: SourceShape[T] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {
      private var index: Int = 0

      override def preStart(): Unit = if (elements.isEmpty) completeStage()

      override def onPull(): Unit =
        if (index < elements.length) {
          push(out, elements(index))
          index += 1
          if (index == elements.length) {
            complete(out)
          }
        } else {
          complete(out)
        }

      setHandler(out, this)
    }

  override def toString: String = "ArraySource"
}
