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

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * Concatenating a `Source.fromJavaStream` to a stream is common enough that it warrants
 * this optimization which avoids the actual fan-out for such cases. Mirrors the
 * resource-management contract of [[JavaStreamSource]]: the underlying `BaseStream` is
 * opened in `preStart` (so any side effects / exceptions from `open()` happen at
 * materialization time, matching the existing concatGraph path) and is guaranteed to
 * be closed via `postStop` on exhaustion, on iterator failure, on downstream cancel,
 * and on stage failure.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class JavaStreamConcat[E](open: () => java.util.stream.BaseStream[E, _])
    extends GraphStage[FlowShape[E, E]] {

  val in = Inlet[E]("JavaStreamConcat.in")
  val out = Outlet[E]("JavaStreamConcat.out")

  override val shape: FlowShape[E, E] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var stream: java.util.stream.BaseStream[E, _] = _
      private var iterator: Iterator[E] = _

      // DO NOT CHANGE
      // WHY: matches JavaStreamSource.preStart which opens the stream at materialization
      // time. Side effects of `open()` (e.g. file/network resource acquisition) and any
      // exceptions it throws happen at the same point as the existing concatGraph path.
      // Deferring open() to onUpstreamFinish would observably delay these effects.
      override def preStart(): Unit = {
        stream = open()
        iterator = stream.iterator().asScala
      }

      override def onPush(): Unit = push(out, grab(in))

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit =
        emitMultiple(out, iterator, () => completeStage())

      override def postStop(): Unit =
        if (stream ne null) {
          try stream.close()
          catch { case NonFatal(_) => () }
        }

      setHandlers(in, out, this)
    }

  override def toString: String = "JavaStreamConcat"
}
