/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.actor.{ DeadLetterSuppression, NoSerializationVerificationNeeded }
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] case object SubscribePending
    extends DeadLetterSuppression
    with NoSerializationVerificationNeeded

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class RequestMore[T](subscription: ActorSubscription[T], demand: Long)
    extends DeadLetterSuppression
    with NoSerializationVerificationNeeded

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Cancel[T](subscription: ActorSubscription[T])
    extends DeadLetterSuppression
    with NoSerializationVerificationNeeded

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class ExposedPublisher(publisher: ActorPublisher[Any])
    extends DeadLetterSuppression
    with NoSerializationVerificationNeeded
