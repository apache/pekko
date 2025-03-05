/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */
package org.apache.pekko.stream.impl.fusing

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.stream.Attributes
import org.apache.pekko.stream.FlowShape
import org.apache.pekko.stream.Graph
import org.apache.pekko.stream.Inlet
import org.apache.pekko.stream.Outlet
import org.apache.pekko.stream.SourceShape
import org.apache.pekko.stream.impl.Stages.DefaultAttributes
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.stage.GraphStage
import org.apache.pekko.stream.stage.GraphStageLogic
import org.apache.pekko.stream.stage.InHandler
import org.apache.pekko.stream.stage.OutHandler

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
    new GraphStageLogic(shape) {

      var source = Option.empty[SubSinkInlet[T]]

      override def preStart(): Unit = {
        pull(in)
        super.preStart()
      }

      setHandler(in,
        new InHandler {
          override def onPush(): Unit = {
            val source = grab(in)
            setSource(source)
            tryPull(in)
          }

          override def onUpstreamFinish(): Unit = if (source.isEmpty) completeStage()
        })

      setHandler(out,
        new OutHandler {
          override def onPull(): Unit = {
            if (isAvailable(out)) tryPushOut()
          }
        })

      def tryPushOut(): Unit = {
        source.foreach { src =>
          if (src.isAvailable) {
            push(out, src.grab())
            if (!src.isClosed) src.pull()
            else removeCurrentSource(completeIfClosed = true)
          }
        }
      }

      def setSource(source: Graph[SourceShape[T], M]): Unit = {
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
        this.source = Some(sinkIn)
        val graph = Source.fromGraph(source).to(sinkIn.sink)
        subFusingMaterializer.materialize(graph, defaultAttributes = enclosingAttributes)
      }

      def removeCurrentSource(completeIfClosed: Boolean): Unit = {
        source = None
        if (completeIfClosed && isClosed(in)) completeStage()
      }

      private def cancelCurrentSource(): Unit = source.foreach(_.cancel())

      override def postStop(): Unit = cancelCurrentSource()

    }

  override def toString: String = s"Switch"
}
