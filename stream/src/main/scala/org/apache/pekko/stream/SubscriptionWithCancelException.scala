/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.util.control.NoStackTrace

import org.reactivestreams.Subscription

import org.apache.pekko.annotation.DoNotInherit

/**
 * Extension of Subscription that allows to pass a cause when a subscription is cancelled.
 *
 * Subscribers can check for this trait and use its `cancel(cause)` method instead of the regular
 * cancel method to pass a cancellation cause.
 *
 * Not for user extension.
 */
@DoNotInherit
trait SubscriptionWithCancelException extends Subscription {
  final override def cancel() = cancel(SubscriptionWithCancelException.NoMoreElementsNeeded)
  def cancel(cause: Throwable): Unit
}
object SubscriptionWithCancelException {

  /**
   * Not for user extension
   */
  @DoNotInherit
  sealed abstract class NonFailureCancellation extends RuntimeException with NoStackTrace
  case object NoMoreElementsNeeded extends NonFailureCancellation
  case object StageWasCompleted extends NonFailureCancellation
}
