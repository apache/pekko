/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.reactivestreams.Subscription

import org.apache.pekko
import pekko.actor.DeadLetterSuppression
import pekko.actor.NoSerializationVerificationNeeded
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] sealed abstract class ActorSubscriberMessage
    extends DeadLetterSuppression
    with NoSerializationVerificationNeeded

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ActorSubscriberMessage {
  final case class OnNext(element: Any) extends ActorSubscriberMessage
  final case class OnError(cause: Throwable) extends ActorSubscriberMessage
  case object OnComplete extends ActorSubscriberMessage

  // OnSubscribe doesn't extend ActorSubscriberMessage by design, because `OnNext`, `OnError` and `OnComplete`
  // are used together, with the same `seal`, but not always `OnSubscribe`.
  final case class OnSubscribe(subscription: Subscription)
      extends DeadLetterSuppression
      with NoSerializationVerificationNeeded

}
