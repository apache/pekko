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
