/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.reactivestreams.{ Publisher, Subscriber, Subscription }

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] case object EmptyPublisher extends Publisher[Nothing] {
  import ReactiveStreamsCompliance._
  override def subscribe(subscriber: Subscriber[_ >: Nothing]): Unit =
    try {
      requireNonNullSubscriber(subscriber)
      tryOnSubscribe(subscriber, CancelledSubscription)
      tryOnComplete(subscriber)
    } catch {
      case _: SpecViolation => // nothing we can do
    }
  def apply[T]: Publisher[T] = this.asInstanceOf[Publisher[T]]
  override def toString: String = "already-completed-publisher"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class ErrorPublisher(t: Throwable, name: String) extends Publisher[Nothing] {
  ReactiveStreamsCompliance.requireNonNullElement(t)

  import ReactiveStreamsCompliance._
  override def subscribe(subscriber: Subscriber[_ >: Nothing]): Unit =
    try {
      requireNonNullSubscriber(subscriber)
      tryOnSubscribe(subscriber, CancelledSubscription)
      tryOnError(subscriber, t)
    } catch {
      case _: SpecViolation => // nothing we can do
    }
  def apply[T]: Publisher[T] = this.asInstanceOf[Publisher[T]]
  override def toString: String = name
}

/**
 * INTERNAL API
 * This is only a legal subscription when it is immediately followed by
 * a termination signal (onComplete, onError).
 */
@InternalApi private[pekko] case object CancelledSubscription extends Subscription {
  override def request(elements: Long): Unit = ()
  override def cancel(): Unit = ()
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class CancellingSubscriber[T] extends Subscriber[T] {
  override def onError(t: Throwable): Unit = ()
  override def onSubscribe(s: Subscription): Unit = s.cancel()
  override def onComplete(): Unit = ()
  override def onNext(t: T): Unit = ()
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] case object RejectAdditionalSubscribers extends Publisher[Nothing] {
  import ReactiveStreamsCompliance._
  override def subscribe(subscriber: Subscriber[_ >: Nothing]): Unit =
    try rejectAdditionalSubscriber(subscriber, "Publisher")
    catch {
      case _: SpecViolation => // nothing we can do
    }
  def apply[T]: Publisher[T] = this.asInstanceOf[Publisher[T]]
  override def toString: String = "already-subscribed-publisher"
}
