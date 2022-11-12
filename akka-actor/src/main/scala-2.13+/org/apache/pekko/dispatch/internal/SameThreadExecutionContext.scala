/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch.internal

import scala.concurrent.ExecutionContext

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * Factory to create same thread ec. Not intended to be called from any other site than to create [[pekko.dispatch.ExecutionContexts#parasitic]]
 *
 * INTERNAL API
 */
@InternalApi
private[dispatch] object SameThreadExecutionContext {
  def apply(): ExecutionContext = ExecutionContext.parasitic
}
