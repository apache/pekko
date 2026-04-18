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

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.ActorAttributes.StreamSubscriptionTimeout
import pekko.stream.Attributes.InputBuffer
import pekko.stream.StreamSubscriptionTimeoutTerminationMode
import pekko.stream._
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{
  AsyncCallback,
  GraphStageLogic,
  GraphStageWithMaterializedValue,
  InHandler,
  TimerGraphStageLogic
}

import org.reactivestreams.{ Publisher, Subscriber }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class FanoutPublisherBridgeStage[T]
    extends GraphStageWithMaterializedValue[SinkShape[T], Publisher[T]] {
  import StreamSubscriptionTimeoutTerminationMode.{ CancelTermination, NoopTermination, WarnTermination }

  private val SubscriptionTimerKey = "FanoutPublisherBridgeStage.subscription-timeout"

  val in: Inlet[T] = Inlet("FanoutPublisherBridgeStage.in")
  override val shape: SinkShape[T] = SinkShape(in)
  override protected def initialAttributes: Attributes = DefaultAttributes.fanoutPublisherSink

  override def toString: String = "FanoutPublisherBridgeStage"

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, Publisher[T]) = {
    object logic extends TimerGraphStageLogic(shape) with InHandler with SubscriberManagement[T] {
      override type S = FanoutPublisherBridgeSubscription[T]

      private val StreamSubscriptionTimeout(timeout, timeoutMode) =
        inheritedAttributes.mandatoryAttribute[StreamSubscriptionTimeout]

      override def initialBufferSize: Int = inheritedAttributes.mandatoryAttribute[InputBuffer].initial
      override def maxBufferSize: Int = inheritedAttributes.mandatoryAttribute[InputBuffer].max

      private var everSubscribed = false
      private var requestedFromUpstream = 0L

      private val requestCallback = getAsyncCallback[(FanoutPublisherBridgeSubscription[T], Long)] {
        case (subscription, elements) =>
          moreRequested(subscription, elements)
          tryPullIfNeeded()
      }

      private val cancelCallback = getAsyncCallback[FanoutPublisherBridgeSubscription[T]] { subscription =>
        unregisterSubscription(subscription)
        tryPullIfNeeded()
      }

      var materializedPublisher: FanoutPublisherBridgePublisher[T] = null

      private val registerPendingSubscribers = getAsyncCallback[Unit] { _ =>
        materializedPublisher.takePendingSubscribers().foreach(registerSubscriber)
        tryPullIfNeeded()
      }

      materializedPublisher = new FanoutPublisherBridgePublisher[T](registerPendingSubscribers)

      override def preStart(): Unit = {
        setKeepGoing(true)
        if (timeoutMode != NoopTermination) scheduleOnce(SubscriptionTimerKey, timeout)
      }

      override protected def onTimer(timerKey: Any): Unit =
        if (timerKey == SubscriptionTimerKey && !everSubscribed) {
          timeoutMode match {
            case CancelTermination =>
              val ex = new SubscriptionTimeoutException(
                s"Subscription timeout expired, no subscriber attached to [$materializedPublisher]") with NoStackTrace
              materializer.logger.warning(
                "Subscription timeout expired for [{}], no subscriber attached in time",
                materializedPublisher)
              materializedPublisher.shutdown(Some(ex))
              failStage(ex)
            case WarnTermination =>
              materializer.logger.warning("Subscription timeout for {}", this)
            case NoopTermination => // won't happen
          }
        }

      override protected def requestFromUpstream(elements: Long): Unit = {
        requestedFromUpstream += elements
        tryPullIfNeeded()
      }

      override protected def cancelUpstream(): Unit =
        if (!isClosed(in)) cancel(in)

      override protected def shutdown(completed: Boolean): Unit = {
        materializedPublisher.shutdown(if (completed) None else ActorPublisher.SomeNormalShutdownReason)
        completeStage()
      }

      override protected def createSubscription(subscriber: Subscriber[_ >: T]): S = {
        everSubscribed = true
        cancelTimer(SubscriptionTimerKey)
        new FanoutPublisherBridgeSubscription[T](subscriber, requestCallback, cancelCallback)
      }

      override def onPush(): Unit = {
        if (requestedFromUpstream <= 0)
          throw new IllegalStateException(s"onPush without outstanding upstream demand: $requestedFromUpstream")
        requestedFromUpstream -= 1
        pushToDownstream(grab(in))
        tryPullIfNeeded()
      }

      override def onUpstreamFinish(): Unit =
        completeDownstream()

      override def onUpstreamFailure(ex: Throwable): Unit = {
        abortDownstream(ex)
        materializedPublisher.shutdown(Some(ex))
        failStage(ex)
      }

      override def postStop(): Unit =
        try abortDownstream(ActorPublisher.NormalShutdownReason)
        finally materializedPublisher.shutdown(ActorPublisher.SomeNormalShutdownReason)

      private def tryPullIfNeeded(): Unit =
        if (requestedFromUpstream > 0 && !hasBeenPulled(in) && !isClosed(in)) pull(in)

      setHandler(in, this)
    }

    (logic, logic.materializedPublisher)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class FanoutPublisherBridgeSubscription[T](
    override val subscriber: Subscriber[_ >: T],
    requestCallback: AsyncCallback[(FanoutPublisherBridgeSubscription[T], Long)],
    cancelCallback: AsyncCallback[FanoutPublisherBridgeSubscription[T]])
    extends SubscriptionWithCursor[T] {

  override def request(elements: Long): Unit =
    requestCallback.invoke((this, elements))

  override def cancel(): Unit =
    cancelCallback.invoke(this)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class FanoutPublisherBridgePublisher[T](
    registerPendingSubscribers: AsyncCallback[Unit])
    extends Publisher[T] {
  import ReactiveStreamsCompliance._

  private val pendingSubscribers = new AtomicReference[immutable.Seq[Subscriber[_ >: T]]](Nil)
  private val shutdownStarted = new AtomicBoolean(false)

  @volatile private var shutdownReason: Option[Throwable] = None

  override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
    requireNonNullSubscriber(subscriber)

    @tailrec def doSubscribe(): Unit = {
      val current = pendingSubscribers.get()
      if (current eq null) reportSubscribeFailure(subscriber)
      else if (pendingSubscribers.compareAndSet(current, subscriber +: current)) registerPendingSubscribers.invoke(())
      else doSubscribe()
    }

    doSubscribe()
  }

  def takePendingSubscribers(): immutable.Seq[Subscriber[_ >: T]] = {
    @tailrec def swapPendingSubscribers(): immutable.Seq[Subscriber[_ >: T]] = {
      val current = pendingSubscribers.get()
      if (current eq null) Nil
      else if (pendingSubscribers.compareAndSet(current, Nil)) current.reverse
      else swapPendingSubscribers()
    }

    swapPendingSubscribers()
  }

  def shutdown(reason: Option[Throwable]): Unit =
    if (shutdownStarted.compareAndSet(false, true)) {
      shutdownReason = reason
      pendingSubscribers.getAndSet(null) match {
        case null    => // already shut down
        case pending =>
          pending.foreach(reportSubscribeFailure)
      }
    }

  private def reportSubscribeFailure(subscriber: Subscriber[_ >: T]): Unit =
    try shutdownReason match {
        case Some(_: ReactiveStreamsCompliance.SpecViolation) => // ok, not allowed to call onError
        case Some(e)                                          =>
          tryOnSubscribe(subscriber, CancelledSubscription)
          tryOnError(subscriber, e)
        case None =>
          tryOnSubscribe(subscriber, CancelledSubscription)
          tryOnComplete(subscriber)
      }
    catch {
      case _: ReactiveStreamsCompliance.SpecViolation => // nothing to do
    }
}
