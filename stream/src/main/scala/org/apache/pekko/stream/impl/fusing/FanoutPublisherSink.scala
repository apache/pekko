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

import org.apache.pekko.NotUsed
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.dispatch.ExecutionContexts
import org.apache.pekko.stream.ActorAttributes.StreamSubscriptionTimeout
import org.apache.pekko.stream.StreamSubscriptionTimeoutTerminationMode.{
  CancelTermination,
  NoopTermination,
  WarnTermination
}
import org.apache.pekko.stream.impl.CancelledSubscription
import org.apache.pekko.stream.impl.Stages.DefaultAttributes
import org.apache.pekko.stream.scaladsl.{ BroadcastHub, Keep, Sink, Source }
import org.apache.pekko.stream.{ Attributes, Inlet, OverflowStrategy, SinkShape }
import org.apache.pekko.stream.stage.{
  AsyncCallback,
  GraphStageLogic,
  GraphStageWithMaterializedValue,
  InHandler,
  OutHandler,
  StageLogging,
  TimerGraphStageLogic
}
import org.reactivestreams.{ Publisher, Subscriber }

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.util.{ Failure, Success }

@InternalApi
private[pekko] object FanoutPublisherSink {
  private trait FanoutPublisherSinkState
  private case class Open[T](callback: AsyncCallback[Subscriber[_ >: T]]) extends FanoutPublisherSinkState
  private case class Closed(failure: Option[Throwable]) extends FanoutPublisherSinkState
  private object TimerKey
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class FanoutPublisherSink[In]
    extends GraphStageWithMaterializedValue[SinkShape[In], Publisher[In]] {
  private val in = Inlet[In]("FanoutPublisherSink.in")
  override val shape: SinkShape[In] = SinkShape.of(in)
  override protected def initialAttributes: Attributes = DefaultAttributes.fanoutPublisherSink
  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Publisher[In]) = {
    val logic: GraphStageLogic with InHandler with Publisher[In] =
      new TimerGraphStageLogic(shape) // for subscribe timeout
        with StageLogging
        with InHandler
        with Publisher[In] {

        import FanoutPublisherSink._
        private[this] val stateRef = new AtomicReference[FanoutPublisherSinkState](
          Open(getAsyncCallback(onSubscribe)))

        private val StreamSubscriptionTimeout(timeout, timeoutMode) =
          inheritedAttributes.mandatoryAttribute[StreamSubscriptionTimeout]
        private val bufferSize = inheritedAttributes.mandatoryAttribute[Attributes.InputBuffer].max
        private val subOutlet = new SubSourceOutlet[In]("FanoutPublisherSink.subSink")
        private var publisherSource: Source[In, NotUsed] = _

        override def onPush(): Unit = {
          subOutlet.push(grab(in))
        }

        override def onUpstreamFinish(): Unit = {
          stateRef.getAndSet(Closed(None))
          subOutlet.complete()
        }
        override def onUpstreamFailure(ex: Throwable): Unit = {
          stateRef.getAndSet(Closed(Some(ex)))
          subOutlet.fail(ex)
        }

        subOutlet.setHandler(new OutHandler {
          override def onPull(): Unit = if (isClosed(in)) completeStage() else pull(in)
          override def onDownstreamFinish(cause: Throwable): Unit =
            if (cause != null) {
              failStage(cause)
            } else {
              completeStage()
            }
        })

        setHandler(in, this)

        override def preStart(): Unit = {
          publisherSource = interpreter.subFusingMaterializer.materialize(
            Source.fromGraph(subOutlet.source).toMat(
              BroadcastHub.sink[In](1, bufferSize))(Keep.right))
          timeoutMode match {
            case NoopTermination => // do nothing
            case _ =>
              scheduleOnce(TimerKey, timeout)
          }
        }

        override protected def onTimer(timerKey: Any): Unit = {
          // no subscriber connected, cancel
          timeoutMode match {
            case CancelTermination =>
              completeStage()
            case WarnTermination =>
              log.warning(
                s"No subscriber has been attached to FanoutPublisherSink after $timeout," +
                "Please cancelling the source stream to avoid memory leaks.")
            case _ => // do nothing
          }
        }

        private def onSubscribe(subscriber: Subscriber[_ >: In]): Unit = {
          cancelTimer(TimerKey)
          interpreter.subFusingMaterializer.materialize(
            publisherSource
              .buffer(1, OverflowStrategy.backpressure) // needed to get the complete signal from the hub.
              .to(Sink.fromSubscriber(subscriber)))
        }

        override def subscribe(subscriber: Subscriber[_ >: In]): Unit = {
          import org.apache.pekko.stream.impl.ReactiveStreamsCompliance._
          requireNonNullSubscriber(subscriber)
          stateRef.get() match {
            case Closed(Some(ex)) =>
              tryOnSubscribe(subscriber, CancelledSubscription)
              tryOnError(subscriber, ex)

            case Closed(None) =>
              tryOnSubscribe(subscriber, CancelledSubscription)
              tryOnComplete(subscriber)

            case Open(callback: AsyncCallback[Subscriber[_]]) =>
              callback
                .invokeWithFeedback(subscriber.asInstanceOf[Subscriber[Any]])
                .onComplete {
                  case Failure(exception) => subscriber.onError(exception)
                  case Success(_)         => // invoked, waiting materialization
                }(ExecutionContexts.parasitic)
            case _ => throw new IllegalArgumentException("Should not happen")
          }
        }

        override def postStop(): Unit = {
          cancelTimer(TimerKey)
          @tailrec
          def tryClose(): Unit = {
            stateRef.get() match {
              case Closed(_) => // do nothing
              case open =>
                if (!stateRef.compareAndSet(open, Closed(None))) {
                  tryClose()
                }
            }
          }
          tryClose()
        }
      }

    (logic, logic)
  }
}
