/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.dispatch

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.dispatch.ForkJoinExecutorConfigurator.PekkoForkJoinTask

import java.util.concurrent.{ ForkJoinPool, ForkJoinTask, TimeUnit }

/**
 * INTERNAL PEKKO USAGE ONLY
 *
 * An alternative version of [[ForkJoinExecutorConfigurator.PekkoForkJoinPool]]
 * that supports the `maximumPoolSize` feature available in [[java.util.concurrent.ForkJoinPool]] in JDK9+.
 */
@InternalApi
private[dispatch] final class PekkoJdk9ForkJoinPool(
    parallelism: Int,
    threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
    maximumPoolSize: Int,
    unhandledExceptionHandler: Thread.UncaughtExceptionHandler,
    asyncMode: Boolean)
    extends ForkJoinPool(parallelism, threadFactory, unhandledExceptionHandler, asyncMode,
      0, maximumPoolSize, 1, null, ForkJoinPoolConstants.DefaultKeepAliveMillis, TimeUnit.MILLISECONDS)
    with LoadMetrics {

  override def execute(r: Runnable): Unit =
    if (r ne null)
      super.execute(
        (if (r.isInstanceOf[ForkJoinTask[_]]) r else new PekkoForkJoinTask(r)).asInstanceOf[ForkJoinTask[Any]])
    else
      throw new NullPointerException("Runnable was null")

  def atFullThrottle(): Boolean = this.getActiveThreadCount() >= this.getParallelism()
}
