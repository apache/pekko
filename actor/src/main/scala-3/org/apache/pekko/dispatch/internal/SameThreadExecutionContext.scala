/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch.internal

import scala.concurrent.ExecutionContext

import org.apache.pekko
import pekko.annotation.InternalApi

import scala.annotation.targetName

/**
 * Factory to create same thread ec. Not intended to be called from any other site than to create [[pekko.dispatch.ExecutionContexts#parasitic]]
 * Remove the @targetName bytecode forwarded methods for Pekko 2.0.x since we only care about source compatibility
 *
 * INTERNAL API
 */
@InternalApi
private[dispatch] object SameThreadExecutionContext {
  inline def apply(): ExecutionContext = ExecutionContext.parasitic

  @targetName("apply")
  def _pekko1Apply(): ExecutionContext = ExecutionContext.parasitic
}
