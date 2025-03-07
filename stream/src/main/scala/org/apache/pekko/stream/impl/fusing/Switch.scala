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
import pekko.stream.Attributes
import pekko.stream.FlowShape
import pekko.stream.Graph
import pekko.stream.Inlet
import pekko.stream.Outlet
import pekko.stream.SourceShape
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.scaladsl.Source
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class Switch[T, M]
    extends GraphStage[FlowShape[Graph[SourceShape[T], M], T]] {
  private val in = Inlet[Graph[SourceShape[T], M]]("switch.in")
  private val out = Outlet[T]("switch.out")

  override def initialAttributes = DefaultAttributes.switch

  override val shape = FlowShape(in, out)

  override def createLogic(enclosingAttributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {

      var source = OptionVal.none[SubSinkInlet[T]]

      override def preStart(): Unit = {
        pull(in)
        super.preStart()
      }

      override def onPush(): Unit = {
        val source = grab(in)
        setupCurrentSource(source)
        tryPull(in)
      }

      override def onUpstreamFinish(): Unit = if (source.isEmpty) completeStage()

      override def onPull(): Unit = {
        if (isAvailable(out)) tryPushOut()
      }

      setHandlers(in, out, this)

      def tryPushOut(): Unit = {
        source match {
          case OptionVal.Some(src) =>
            if (src.isAvailable) {
              push(out, src.grab())
              if (!src.isClosed) src.pull()
              else removeCurrentSource(completeIfClosed = true)
            }
          case OptionVal.None =>
        }
      }

      def setupCurrentSource(source: Graph[SourceShape[T], M]): Unit = {
        cancelCurrentSource()
        removeCurrentSource(completeIfClosed = false)
        val sinkIn = new SubSinkInlet[T]("SwitchSink")
        sinkIn.setHandler(new InHandler {
          override def onPush(): Unit = {
            if (isAvailable(out)) {
              push(out, sinkIn.grab())
              sinkIn.pull()
            }
          }

          override def onUpstreamFinish(): Unit = {
            if (!sinkIn.isAvailable) removeCurrentSource(completeIfClosed = true)
          }
        })
        sinkIn.pull()
        this.source = OptionVal.Some(sinkIn)
        val graph = Source.fromGraph(source).to(sinkIn.sink)
        subFusingMaterializer.materialize(graph, defaultAttributes = enclosingAttributes)
      }

      def removeCurrentSource(completeIfClosed: Boolean): Unit = {
        source = OptionVal.none
        if (completeIfClosed && isClosed(in)) completeStage()
      }

      private def cancelCurrentSource(): Unit = {
        source match {
          case OptionVal.Some(src) =>
            src.cancel()
          case OptionVal.None =>
        }
      }

      override def postStop(): Unit = cancelCurrentSource()

    }

  override def toString: String = s"Switch"
}
