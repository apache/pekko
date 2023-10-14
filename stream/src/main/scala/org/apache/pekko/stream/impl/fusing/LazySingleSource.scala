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
import pekko.stream.Attributes
import pekko.stream.Attributes.SourceLocation
import pekko.stream.Outlet
import pekko.stream.SourceShape
import pekko.stream.impl.ReactiveStreamsCompliance
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.OutHandler

private[pekko] final class LazySingleSource[T](f: () => T) extends GraphStage[SourceShape[T]] {
  require(f != null, "f should not be null.")
  private val out = Outlet[T]("LazySingleSource.out")
  override def initialAttributes: Attributes = DefaultAttributes.lazySingleSource and
    SourceLocation.forLambda(f)

  val shape = SourceShape(out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {
      override def onPull(): Unit = {
        setHandler(out, eagerTerminateOutput) // After first pull we won't produce anything more
        val elem = f()
        ReactiveStreamsCompliance.requireNonNullElement(elem)
        push(out, elem)
        completeStage()
      }

      setHandler(out, this)
    }
  override def toString: String = "LazySingleSource"
}
