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
import org.apache.pekko.stream.impl.Stages.DefaultAttributes
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.stream.{ ActorAttributes, Attributes, Inlet, SinkShape, StreamSubscriptionTimeoutTerminationMode }
import pekko.stream.ActorAttributes.StreamSubscriptionTimeout
import pekko.stream.scaladsl.Source
import pekko.stream.stage.{
  GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler, TimerGraphStageLogic
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object SourceSink
    extends GraphStageWithMaterializedValue[SinkShape[Any], Source[Any, NotUsed]] {
  private val SubscriptionTimerKey = "SubstreamSubscriptionTimerKey"
  private val in = Inlet[Any]("sourceSink.in")
  override val shape = SinkShape(in)

  override def toString: String = "SourceSink"
  override protected def initialAttributes: Attributes = DefaultAttributes.sourceSink

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, Source[Any, NotUsed]) = {

    /*
     * NOTE: in the current implementation of Pekko Stream,
     * We have to materialize twice to do the piping, which means that we can treat the Sink as a Source.
     *
     * In an ideal world, this stage should be purged out by the materializer optimization,
     * and we can directly connect the upstream to the downstream.
     */
    object logic extends TimerGraphStageLogic(shape) with InHandler with OutHandler { self =>
      val sinkSource = new SubSourceOutlet[Any]("sinkSource")

      private def subHandler(): OutHandler = new OutHandler {
        override def onPull(): Unit = {
          setKeepGoing(false)
          cancelTimer(SubscriptionTimerKey)
          pull(in)
          sinkSource.setHandler(self)
        }
        override def onDownstreamFinish(cause: Throwable): Unit = self.onDownstreamFinish(cause)
      }

      override def preStart(): Unit = {
        sinkSource.setHandler(subHandler())
        setKeepGoing(true)
        val timeout = inheritedAttributes.mandatoryAttribute[ActorAttributes.StreamSubscriptionTimeout].timeout
        scheduleOnce(SubscriptionTimerKey, timeout)
      }

      override protected def onTimer(timerKey: Any): Unit = {
        val materializer = interpreter.materializer
        val StreamSubscriptionTimeout(timeout, mode) =
          inheritedAttributes.mandatoryAttribute[ActorAttributes.StreamSubscriptionTimeout]

        mode match {
          case StreamSubscriptionTimeoutTerminationMode.CancelTermination =>
            sinkSource.timeout(timeout)
            if (sinkSource.isClosed)
              completeStage()
          case StreamSubscriptionTimeoutTerminationMode.NoopTermination =>
          // do nothing
          case StreamSubscriptionTimeoutTerminationMode.WarnTermination =>
            materializer.logger.warning(
              "Substream subscription timeout triggered after {} in SourceSink.",
              timeout)
        }
      }

      override def onPush(): Unit = sinkSource.push(grab(in))
      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit = {
        if (!sinkSource.isClosed) {
          sinkSource.complete()
        }
        completeStage()
      }

      override def onUpstreamFailure(ex: Throwable): Unit = if (!sinkSource.isClosed) {
        sinkSource.fail(ex)
        completeStage()
      } else failStage(ex)

      override def onDownstreamFinish(cause: Throwable): Unit = {
        // cancel upstream only if the substream was cancelled
        if (!isClosed(in)) cancelStage(cause)
      }

      setHandler(in, this)
    }

    (logic, Source.fromGraph(logic.sinkSource.source))
  }
}
