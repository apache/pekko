/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal

import org.apache.pekko
import pekko.actor.WrappedMessage
import pekko.annotation.InternalApi

/**
 * A marker trait for internal messages.
 */
@InternalApi private[pekko] sealed trait InternalMessage

/**
 * INTERNAL API: Wrapping of messages that should be adapted by
 * adapters registered with `ActorContext.messageAdapter`.
 */
@InternalApi private[pekko] final case class AdaptWithRegisteredMessageAdapter[U](msg: U) extends InternalMessage

/**
 * INTERNAL API: Wrapping of messages that should be adapted by the included
 * function. Used by `ActorContext.spawnMessageAdapter` and `ActorContext.ask` so that the function is
 * applied in the "parent" actor (for better thread safety)..
 */
@InternalApi private[pekko] final case class AdaptMessage[U, T](message: U, adapter: U => T)
    extends InternalMessage
    with WrappedMessage {
  def adapt(): T = adapter(message)
}
