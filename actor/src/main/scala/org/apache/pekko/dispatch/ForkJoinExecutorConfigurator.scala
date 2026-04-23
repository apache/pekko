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

package org.apache.pekko.dispatch

import java.util.concurrent.{ ExecutorService, ForkJoinPool, ForkJoinTask, ThreadFactory, TimeUnit }

import com.typesafe.config.Config

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.util.JavaVersion

object ForkJoinExecutorConfigurator {

  /**
   * INTERNAL API
   *
   * Resolves the effective `minimum-runnable` value for a fork-join dispatcher.
   *
   * A negative value (default `-1` in reference.conf) selects the JDK-aware policy:
   * on JDK 25+ the value is `min(8, max(1, parallelism / 2))` to mitigate the
   * asyncMode (FIFO) compensation-thread regression tracked in
   * JDK-8300995 / JDK-8321335 (the impact is most visible on the JDK 25 line in
   * Pekko nightly tests); on older JDKs the value stays at `1` to preserve the
   * pre-existing behaviour. Non-negative configured values are honoured verbatim,
   * so `0` still disables compensation entirely.
   */
  @InternalApi private[pekko] def resolveMinimumRunnable(
      configured: Int,
      parallelism: Int,
      jdkMajorVersion: Int): Int =
    if (configured >= 0) configured
    else if (jdkMajorVersion >= 25) math.min(8, math.max(1, parallelism / 2))
    else 1

  /**
   * INTERNAL PEKKO USAGE ONLY
   */
  final class PekkoForkJoinPool(
      parallelism: Int,
      threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
      maximumPoolSize: Int,
      unhandledExceptionHandler: Thread.UncaughtExceptionHandler,
      asyncMode: Boolean,
      minimumRunnable: Int = 1)
      extends ForkJoinPool(parallelism, threadFactory, unhandledExceptionHandler, asyncMode,
        0, maximumPoolSize, minimumRunnable, null, ForkJoinPoolConstants.DefaultKeepAliveMillis, TimeUnit.MILLISECONDS)
      with LoadMetrics {

    override def execute(r: Runnable): Unit =
      if (r ne null)
        super.execute(
          (if (r.isInstanceOf[ForkJoinTask[_]]) r else new PekkoForkJoinTask(r)).asInstanceOf[ForkJoinTask[Any]])
      else
        throw new NullPointerException("Runnable was null")

    def atFullThrottle(): Boolean = this.getActiveThreadCount() >= this.getParallelism()
  }

  /**
   * INTERNAL PEKKO USAGE ONLY
   */
  @SerialVersionUID(1L)
  final class PekkoForkJoinTask(runnable: Runnable) extends ForkJoinTask[Unit] {
    override def getRawResult(): Unit = ()
    override def setRawResult(unit: Unit): Unit = ()
    override def exec(): Boolean =
      try {
        runnable.run(); true
      } catch {
        case _: InterruptedException =>
          Thread.currentThread.interrupt()
          false
        case anything: Throwable =>
          val t = Thread.currentThread()
          t.getUncaughtExceptionHandler match {
            case null =>
            case some => some.uncaughtException(t, anything)
          }
          throw anything
      }
  }
}

class ForkJoinExecutorConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
    extends ExecutorServiceConfigurator(config, prerequisites) {
  import ForkJoinExecutorConfigurator._
  final override val isVirtualized: Boolean = config.getBoolean("virtualize") && VirtualThreadSupport.isSupported
  override def virtualThreadStartNumber: Int = config.getInt("virtual-thread-start-number")

  def validate(t: ThreadFactory): ForkJoinPool.ForkJoinWorkerThreadFactory = t match {
    case correct: ForkJoinPool.ForkJoinWorkerThreadFactory => correct
    case _                                                 =>
      throw new IllegalStateException(
        "The prerequisites for the ForkJoinExecutorConfigurator is a ForkJoinPool.ForkJoinWorkerThreadFactory!")
  }

  class ForkJoinExecutorServiceFactory(
      val id: String,
      val threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
      val parallelism: Int,
      val asyncMode: Boolean,
      val maxPoolSize: Int,
      val minimumRunnable: Int = 1)
      extends ExecutorServiceFactory {
    def this(threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
        parallelism: Int,
        asyncMode: Boolean,
        maxPoolSize: Int) =
      this(null, threadFactory, parallelism, asyncMode, maxPoolSize)

    def this(threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
        parallelism: Int,
        asyncMode: Boolean) = this(threadFactory, parallelism, asyncMode, ForkJoinPoolConstants.MaxCap)

    def this(threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory, parallelism: Int) =
      this(threadFactory, parallelism, asyncMode = true)

    def createExecutorService: ExecutorService = {
      val tf = if (isVirtualized) {
        threadFactory match {
          // we need to use the thread factory to create carrier thread
          case m: MonitorableThreadFactory => new MonitorableCarrierThreadFactory(m.name)
          case _                           => threadFactory
        }
      } else threadFactory

      val pool = new PekkoForkJoinPool(parallelism, tf, maxPoolSize, MonitorableThreadFactory.doNothing, asyncMode,
        minimumRunnable)

      if (isVirtualized) {
        // we need to cast here,
        createVirtualized(threadFactory.asInstanceOf[ThreadFactory], pool, id, virtualThreadStartNumber)
      } else {
        pool
      }
    }
  }

  final def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = {
    val tf = threadFactory match {
      case m: MonitorableThreadFactory =>
        // add the dispatcher id to the thread names
        m.withName(m.name + "-" + id)
      case other => other
    }

    val asyncMode = config.getString("task-peeking-mode") match {
      case "FIFO" => true
      case "LIFO" => false
      case _      =>
        throw new IllegalArgumentException(
          "Cannot instantiate ForkJoinExecutorServiceFactory. " +
          """"task-peeking-mode" in "fork-join-executor" section could only set to "FIFO" or "LIFO".""")
    }

    val parallelism = ThreadPoolConfig.scaledPoolSize(
      config.getInt("parallelism-min"),
      config.getDouble("parallelism-factor"),
      config.getInt("parallelism-max"))

    new ForkJoinExecutorServiceFactory(
      id,
      validate(tf),
      parallelism,
      asyncMode,
      config.getInt("maximum-pool-size"),
      ForkJoinExecutorConfigurator.resolveMinimumRunnable(
        config.getInt("minimum-runnable"),
        parallelism,
        JavaVersion.majorVersion)
    )
  }
}
