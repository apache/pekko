/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal

import org.apache.pekko.annotation.InternalApi

import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ActorMdc {
  val SourceActorSystemKey = "sourceActorSystem"
  val PekkoSourceKey = "pekkoSource"
  val PekkoTagsKey = "pekkoTags"
  val PekkoAddressKey = "pekkoAddress"
  // Mirrors the classic Slf4jLogger MDC attribute (Slf4jLogger.mdcThreadAttributeName) so that
  // typed and classic actor log entries can both be correlated with the dispatching thread.
  val SourceThreadKey = "sourceThread"

  def setMdc(context: ActorContextImpl.LoggingContext): Unit = {
    // avoid access to MDC ThreadLocal if not needed, see details in LoggingContext
    context.mdcUsed = true
    MDC.put(PekkoSourceKey, context.pekkoSource)
    MDC.put(SourceActorSystemKey, context.sourceActorSystem)
    MDC.put(PekkoAddressKey, context.pekkoAddress)
    // Typed actors log synchronously on the dispatcher thread that runs the actor, so the current
    // thread is the source thread the user's log statements execute on.
    MDC.put(SourceThreadKey, Thread.currentThread().getName)
    // empty string for no tags, a single tag or a comma separated list of tags
    if (context.tagsString.nonEmpty)
      MDC.put(PekkoTagsKey, context.tagsString)
  }

  // MDC is cleared (if used) from aroundReceive in ActorAdapter after processing each message,
  // via ActorContextImpl.clearMdc()
  def clearMdc(): Unit =
    MDC.clear()

}
