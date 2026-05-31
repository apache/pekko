/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor._
import pekko.annotation.InternalApi
import pekko.event.Logging
import pekko.stream.impl.ActorSubscriberMessage.{ OnComplete, OnError, OnNext, OnSubscribe }

import org.reactivestreams.{ Subscriber, Subscription }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] abstract class BatchingInputBuffer(val size: Int, val pump: Pump)
    extends DefaultInputTransferStates {
  if (size < 1) throw new IllegalArgumentException(s"buffer size must be positive (was: $size)")
  if ((size & (size - 1)) != 0) throw new IllegalArgumentException(s"buffer size must be a power of two (was: $size)")

  // TODO: buffer and batch sizing heuristics
  private var upstream: Subscription = _
  private val inputBuffer = new Array[AnyRef](size)
  private var inputBufferElements = 0
  private var nextInputElementCursor = 0
  private var upstreamCompleted = false
  private val IndexMask = size - 1

  private def requestBatchSize = math.max(1, inputBuffer.length / 2)
  private var batchRemaining = requestBatchSize

  override def toString: String =
    s"BatchingInputBuffer(size=$size, elems=$inputBufferElements, completed=$upstreamCompleted, remaining=$batchRemaining)"

  override val subreceive: SubReceive = new SubReceive(waitingForUpstream)

  override def dequeueInputElement(): Any = {
    val elem = inputBuffer(nextInputElementCursor)
    inputBuffer(nextInputElementCursor) = null

    batchRemaining -= 1
    if (batchRemaining == 0 && !upstreamCompleted) {
      upstream.request(requestBatchSize)
      batchRemaining = requestBatchSize
    }

    inputBufferElements -= 1
    nextInputElementCursor += 1
    nextInputElementCursor &= IndexMask
    elem
  }

  protected final def enqueueInputElement(elem: Any): Unit = {
    if (isOpen) {
      if (inputBufferElements == size) throw new IllegalStateException("Input buffer overrun")
      inputBuffer((nextInputElementCursor + inputBufferElements) & IndexMask) = elem.asInstanceOf[AnyRef]
      inputBufferElements += 1
    }
    pump.pump()
  }

  override def cancel(): Unit = {
    if (!upstreamCompleted) {
      upstreamCompleted = true
      if (upstream ne null) upstream.cancel()
      clear()
    }
  }
  override def isClosed: Boolean = upstreamCompleted

  private def clear(): Unit = {
    java.util.Arrays.fill(inputBuffer, 0, inputBuffer.length, null)
    inputBufferElements = 0
  }

  override def inputsDepleted = upstreamCompleted && inputBufferElements == 0
  override def inputsAvailable = inputBufferElements > 0

  protected def onComplete(): Unit = {
    upstreamCompleted = true
    subreceive.become(completed)
    pump.pump()
  }

  protected def onSubscribe(subscription: Subscription): Unit = {
    ReactiveStreamsCompliance.requireNonNullSubscription(subscription)
    if (upstreamCompleted) subscription.cancel()
    else {
      upstream = subscription
      // Prefetch
      upstream.request(inputBuffer.length)
      subreceive.become(upstreamRunning)
    }
    pump.gotUpstreamSubscription()
  }

  protected def onError(e: Throwable): Unit = {
    upstreamCompleted = true
    subreceive.become(completed)
    inputOnError(e)
  }

  protected def waitingForUpstream: Actor.Receive = {
    case OnComplete                => onComplete()
    case OnSubscribe(subscription) => onSubscribe(subscription)
    case OnError(cause)            => onError(cause)
  }

  protected def upstreamRunning: Actor.Receive = {
    case OnNext(element)           => enqueueInputElement(element)
    case OnComplete                => onComplete()
    case OnError(cause)            => onError(cause)
    case OnSubscribe(subscription) => subscription.cancel() // spec rule 2.5
  }

  protected def completed: Actor.Receive = {
    case OnSubscribe(_) => throw new IllegalStateException("onSubscribe called after onError or onComplete")
  }

  protected def inputOnError(@nowarn("msg=never used") e: Throwable): Unit = {
    clear()
  }

}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class SimpleOutputs(val actor: ActorRef, val pump: Pump)
    extends DefaultOutputTransferStates {
  import ReactiveStreamsCompliance._

  protected var exposedPublisher: ActorPublisher[Any] = _

  protected var subscriber: Subscriber[Any] = _
  protected var downstreamDemand: Long = 0L
  protected var downstreamCompleted = false
  override def demandAvailable = downstreamDemand > 0
  override def demandCount: Long = downstreamDemand

  override def subreceive = _subreceive
  private val _subreceive = new SubReceive(waitingExposedPublisher)

  def isSubscribed = subscriber ne null

  def enqueueOutputElement(elem: Any): Unit = {
    ReactiveStreamsCompliance.requireNonNullElement(elem)
    downstreamDemand -= 1
    tryOnNext(subscriber, elem)
  }

  override def complete(): Unit = {
    if (!downstreamCompleted) {
      downstreamCompleted = true
      if (exposedPublisher ne null) exposedPublisher.shutdown(None)
      if (subscriber ne null) tryOnComplete(subscriber)
    }
  }

  override def cancel(): Unit = {
    if (!downstreamCompleted) {
      downstreamCompleted = true
      if (exposedPublisher ne null) exposedPublisher.shutdown(None)
    }
  }

  override def error(e: Throwable): Unit = {
    if (!downstreamCompleted) {
      downstreamCompleted = true
      if (exposedPublisher ne null) exposedPublisher.shutdown(Some(e))
      if ((subscriber ne null) && !e.isInstanceOf[SpecViolation]) tryOnError(subscriber, e)
    }
  }

  override def isClosed: Boolean = downstreamCompleted && (subscriber ne null)

  protected def createSubscription(): Subscription = new ActorSubscription(actor, subscriber)

  private def subscribePending(subscribers: Seq[Subscriber[Any]]): Unit =
    subscribers.foreach { sub =>
      if (subscriber eq null) {
        subscriber = sub
        tryOnSubscribe(subscriber, createSubscription())
      } else
        rejectAdditionalSubscriber(sub, s"${Logging.simpleName(this)}")
    }

  protected def waitingExposedPublisher: Actor.Receive = {
    case ExposedPublisher(publisher) =>
      exposedPublisher = publisher
      subreceive.become(downstreamRunning)
    case other =>
      throw new IllegalStateException(s"The first message must be ExposedPublisher but was [$other]")
  }

  protected def downstreamRunning: Actor.Receive = {
    case SubscribePending =>
      subscribePending(exposedPublisher.takePendingSubscribers().asInstanceOf[Seq[Subscriber[Any]]])
    case RequestMore(_, elements) =>
      if (elements < 1) {
        error(ReactiveStreamsCompliance.numberOfElementsInRequestMustBePositiveException)
      } else {
        downstreamDemand += elements
        if (downstreamDemand < 1)
          downstreamDemand = Long.MaxValue // Long overflow, Reactive Streams Spec 3:17: effectively unbounded
        pump.pump()
      }
    case Cancel(_) =>
      downstreamCompleted = true
      exposedPublisher.shutdown(Some(new ActorPublisher.NormalShutdownException))
      pump.pump()
  }

}
