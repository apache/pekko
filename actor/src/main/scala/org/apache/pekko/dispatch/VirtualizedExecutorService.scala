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

import org.apache.pekko.annotation.InternalApi

import java.util
import java.util.concurrent.{ Callable, Executor, ExecutorService, Future, ThreadFactory, TimeUnit }

/**
 * A virtualized executor service that creates a new virtual thread for each task.
 * Will shut down the underlying executor service when this executor is being shutdown.
 *
 * INTERNAL API
 */
@InternalApi
final class VirtualizedExecutorService(
    vtFactory: ThreadFactory,
    underlying: ExecutorService,
    loadMetricsProvider: Executor => Boolean,
    cascadeShutdown: Boolean)
    extends ExecutorService with LoadMetrics {
  require(VirtualThreadSupport.isSupported, "Virtual thread is not supported.")
  require(vtFactory != null, "Virtual thread factory must not be null")
  require(loadMetricsProvider != null, "Load metrics provider must not be null")

  def this(prefix: String,
      underlying: ExecutorService,
      loadMetricsProvider: Executor => Boolean,
      cascadeShutdown: Boolean) = {
    this(VirtualThreadSupport.newVirtualThreadFactory(prefix), underlying, loadMetricsProvider, cascadeShutdown)
  }

  private val executor = VirtualThreadSupport.newThreadPerTaskExecutor(vtFactory)

  override def atFullThrottle(): Boolean = loadMetricsProvider(this)

  override def shutdown(): Unit = {
    executor.shutdown()
    if (cascadeShutdown && (underlying ne null)) {
      underlying.shutdown()
    }
  }

  override def shutdownNow(): util.List[Runnable] = {
    val r = executor.shutdownNow()
    if (cascadeShutdown && (underlying ne null)) {
      underlying.shutdownNow()
    }
    r
  }

  override def isShutdown: Boolean = {
    if (cascadeShutdown) {
      executor.isShutdown && ((underlying eq null) || underlying.isShutdown)
    } else {
      executor.isShutdown
    }
  }

  override def isTerminated: Boolean = {
    if (cascadeShutdown) {
      executor.isTerminated && ((underlying eq null) || underlying.isTerminated)
    } else {
      executor.isTerminated
    }
  }

  override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    if (cascadeShutdown) {
      executor.awaitTermination(timeout, unit) && ((underlying eq null) || underlying.awaitTermination(timeout, unit))
    } else {
      executor.awaitTermination(timeout, unit)
    }
  }

  override def submit[T](task: Callable[T]): Future[T] = {
    executor.submit(task)
  }

  override def submit[T](task: Runnable, result: T): Future[T] = {
    executor.submit(task, result)
  }

  override def submit(task: Runnable): Future[_] = {
    executor.submit(task)
  }

  override def invokeAll[T](tasks: util.Collection[_ <: Callable[T]]): util.List[Future[T]] = {
    executor.invokeAll(tasks)
  }

  override def invokeAll[T](
      tasks: util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): util.List[Future[T]] = {
    executor.invokeAll(tasks, timeout, unit)
  }

  override def invokeAny[T](tasks: util.Collection[_ <: Callable[T]]): T = {
    executor.invokeAny(tasks)
  }

  override def invokeAny[T](tasks: util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T = {
    executor.invokeAny(tasks, timeout, unit)
  }

  override def execute(command: Runnable): Unit = {
    executor.execute(command)
  }
}
