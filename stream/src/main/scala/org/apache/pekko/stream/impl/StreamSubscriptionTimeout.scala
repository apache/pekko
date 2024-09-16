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
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.actor._
import pekko.annotation.InternalApi
import pekko.stream.StreamSubscriptionTimeoutSettings
import pekko.stream.StreamSubscriptionTimeoutTerminationMode.{ CancelTermination, NoopTermination, WarnTermination }

import org.reactivestreams._

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object StreamSubscriptionTimeoutSupport {

  /**
   * A subscriber who calls `cancel` directly from `onSubscribe` and ignores all other callbacks.
   */
  case object CancelingSubscriber extends Subscriber[Any] {
    override def onSubscribe(s: Subscription): Unit = {
      ReactiveStreamsCompliance.requireNonNullSubscription(s)
      s.cancel()
    }
    override def onError(t: Throwable): Unit = {
      ReactiveStreamsCompliance.requireNonNullException(t)
      ()
    }
    override def onComplete(): Unit = ()
    override def onNext(elem: Any): Unit = {
      ReactiveStreamsCompliance.requireNonNullElement(elem)
      ()
    }
  }

  /**
   * INTERNAL API
   *
   * Subscription timeout which does not start any scheduled events and always returns `true`.
   * This specialized implementation is to be used for "noop" timeout mode.
   */
  @InternalApi private[pekko] case object NoopSubscriptionTimeout extends Cancellable {
    override def cancel() = true
    override def isCancelled = true
  }
}

/**
 * INTERNAL API
 * Provides support methods to create Publishers and Subscribers which time-out gracefully,
 * and are canceled subscribing an `CancellingSubscriber` to the publisher, or by calling `onError` on the timed-out subscriber.
 *
 * See `pekko.stream.materializer.subscription-timeout` for configuration options.
 */
@nowarn("msg=deprecated")
@InternalApi private[pekko] trait StreamSubscriptionTimeoutSupport {
  this: Actor with ActorLogging =>

  import StreamSubscriptionTimeoutSupport._

  /**
   * Default settings for subscription timeouts.
   */
  protected def subscriptionTimeoutSettings: StreamSubscriptionTimeoutSettings

  /**
   * Schedules a Subscription timeout.
   * The actor will receive the message created by the provided block if the timeout triggers.
   */
  protected def scheduleSubscriptionTimeout(actor: ActorRef, message: Any): Cancellable =
    subscriptionTimeoutSettings.mode match {
      case NoopTermination =>
        NoopSubscriptionTimeout
      case _ =>
        import context.dispatcher
        val cancellable = context.system.scheduler.scheduleOnce(subscriptionTimeoutSettings.timeout, actor, message)
        cancellable
    }

  private def cancel(target: Publisher[_], timeout: FiniteDuration): Unit = {
    val millis = timeout.toMillis
    target match {
      case p: Processor[_, _] =>
        log.debug("Cancelling {} Processor's publisher and subscriber sides (after {} ms)", p, millis)
        handleSubscriptionTimeout(
          target,
          new SubscriptionTimeoutException(s"Publisher was not attached to upstream within deadline ($millis) ms")
            with NoStackTrace)

      case p: Publisher[_] =>
        log.debug("Cancelling {} (after: {} ms)", p, millis)
        handleSubscriptionTimeout(
          target,
          new SubscriptionTimeoutException(
            s"Publisher ($p) you are trying to subscribe to has been shut-down " +
            s"because exceeding it's subscription-timeout.") with NoStackTrace)
    }
  }

  private def warn(target: Publisher[_], timeout: FiniteDuration): Unit =
    log.warning(
      "Timed out {} detected (after {} ms)! You should investigate if you either cancel or consume all {} instances",
      target,
      timeout.toMillis,
      target.getClass.getCanonicalName)

  /**
   * Called by the actor when a subscription has timed out. Expects the actual `Publisher` or `Processor` target.
   */
  @nowarn("msg=deprecated")
  protected def subscriptionTimedOut(target: Publisher[_]): Unit = subscriptionTimeoutSettings.mode match {
    case NoopTermination   => // ignore...
    case WarnTermination   => warn(target, subscriptionTimeoutSettings.timeout)
    case CancelTermination => cancel(target, subscriptionTimeoutSettings.timeout)
  }

  /**
   * Callback that should ensure that the target is canceled with the given cause.
   */
  protected def handleSubscriptionTimeout(target: Publisher[_], cause: Exception): Unit
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class SubscriptionTimeoutException(msg: String) extends RuntimeException(msg)
