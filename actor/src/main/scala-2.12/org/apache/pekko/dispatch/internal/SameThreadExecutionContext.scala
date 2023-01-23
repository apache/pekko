/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch.internal

import scala.concurrent.ExecutionContext

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.dispatch.BatchingExecutor

/**
 * Factory to create same thread ec. Not intended to be called from any other site than to create [[pekko.dispatch.ExecutionContexts#parasitic]]
 *
 * INTERNAL API
 */
@InternalApi
private[dispatch] object SameThreadExecutionContext {

  private val sameThread = new ExecutionContext with BatchingExecutor {
    override protected def unbatchedExecute(runnable: Runnable): Unit = runnable.run()
    override protected def resubmitOnBlock: Boolean = false // No point since we execute on same thread
    override def reportFailure(t: Throwable): Unit =
      throw new IllegalStateException("exception in sameThreadExecutionContext", t)
  }

  def apply(): ExecutionContext = sameThread

}
