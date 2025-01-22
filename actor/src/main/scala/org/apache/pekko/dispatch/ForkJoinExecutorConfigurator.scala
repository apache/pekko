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

import com.typesafe.config.Config
import org.apache.pekko
import pekko.dispatch.VirtualThreadSupport.newVirtualThreadFactory
import pekko.util.JavaVersion

import java.lang.invoke.{ MethodHandle, MethodHandles, MethodType }
import java.util.concurrent.{ Executor, ExecutorService, ForkJoinPool, ForkJoinTask, ThreadFactory }
import scala.util.Try

object ForkJoinExecutorConfigurator {

  /**
   * INTERNAL PEKKO USAGE ONLY
   */
  final class PekkoForkJoinPool(
      parallelism: Int,
      threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
      unhandledExceptionHandler: Thread.UncaughtExceptionHandler,
      asyncMode: Boolean)
      extends ForkJoinPool(parallelism, threadFactory, unhandledExceptionHandler, asyncMode)
      with LoadMetrics {
    def this(
        parallelism: Int,
        threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
        unhandledExceptionHandler: Thread.UncaughtExceptionHandler) =
      this(parallelism, threadFactory, unhandledExceptionHandler, asyncMode = true)

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

  def validate(t: ThreadFactory): ForkJoinPool.ForkJoinWorkerThreadFactory = t match {
    case correct: ForkJoinPool.ForkJoinWorkerThreadFactory => correct
    case _ =>
      throw new IllegalStateException(
        "The prerequisites for the ForkJoinExecutorConfigurator is a ForkJoinPool.ForkJoinWorkerThreadFactory!")
  }

  class ForkJoinExecutorServiceFactory(
      val id: String,
      val threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
      val parallelism: Int,
      val asyncMode: Boolean,
      val maxPoolSize: Int,
      val virtualize: Boolean)
      extends ExecutorServiceFactory {
    def this(threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
        parallelism: Int,
        asyncMode: Boolean,
        maxPoolSize: Int,
        virtualize: Boolean) =
      this(null, threadFactory, parallelism, asyncMode, maxPoolSize, virtualize)

    def this(threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
        parallelism: Int,
        asyncMode: Boolean) = this(threadFactory, parallelism, asyncMode, ForkJoinPoolConstants.MaxCap, false)

    def this(threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
        parallelism: Int,
        asyncMode: Boolean,
        maxPoolSize: Int) = this(threadFactory, parallelism, asyncMode, maxPoolSize, false)

    private def pekkoJdk9ForkJoinPoolClassOpt: Option[Class[_]] =
      Try(Class.forName("org.apache.pekko.dispatch.PekkoJdk9ForkJoinPool")).toOption

    private lazy val pekkoJdk9ForkJoinPoolHandleOpt: Option[MethodHandle] = {
      if (JavaVersion.majorVersion == 8) {
        None
      } else {
        pekkoJdk9ForkJoinPoolClassOpt.map { clz =>
          val methodHandleLookup = MethodHandles.lookup()
          val mt = MethodType.methodType(classOf[Unit], classOf[Int],
            classOf[ForkJoinPool.ForkJoinWorkerThreadFactory],
            classOf[Int], classOf[Thread.UncaughtExceptionHandler], classOf[Boolean])
          methodHandleLookup.findConstructor(clz, mt)
        }
      }
    }

    def this(threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory, parallelism: Int) =
      this(threadFactory, parallelism, asyncMode = true)

    def createExecutorService: ExecutorService = {
      val tf = if (virtualize && JavaVersion.majorVersion >= 21) {
        threadFactory match {
          // we need to use the thread factory to create carrier thread
          case m: MonitorableThreadFactory => new MonitorableCarrierThreadFactory(m.name)
          case _                           => threadFactory
        }
      } else threadFactory

      val pool = pekkoJdk9ForkJoinPoolHandleOpt match {
        case Some(handle) =>
          // carrier Thread only exists in JDK 17+
          handle.invoke(parallelism, tf, maxPoolSize, MonitorableThreadFactory.doNothing, asyncMode)
            .asInstanceOf[ExecutorService with LoadMetrics]
        case _ =>
          new PekkoForkJoinPool(parallelism, tf, MonitorableThreadFactory.doNothing, asyncMode)
      }

      if (virtualize && JavaVersion.majorVersion >= 21) {
        // when virtualized, we need enhanced thread factory
        val factory: ThreadFactory = threadFactory match {
          case MonitorableThreadFactory(name, _, contextClassLoader, exceptionHandler, _) =>
            new ThreadFactory {
              private val vtFactory = newVirtualThreadFactory(name, pool) // use the pool as the scheduler

              override def newThread(r: Runnable): Thread = {
                val vt = vtFactory.newThread(r)
                vt.setUncaughtExceptionHandler(exceptionHandler)
                contextClassLoader.foreach(vt.setContextClassLoader)
                vt
              }
            }
          case _ => newVirtualThreadFactory(prerequisites.settings.name, pool); // use the pool as the scheduler
        }
        // wrap the pool with virtualized executor service
        new VirtualizedExecutorService(
          factory, // the virtual thread factory
          pool, // the underlying pool
          (_: Executor) => pool.atFullThrottle(), // the load metrics provider, we use the pool itself
          cascadeShutdown = true // cascade shutdown
        )
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
      case _ =>
        throw new IllegalArgumentException(
          "Cannot instantiate ForkJoinExecutorServiceFactory. " +
          """"task-peeking-mode" in "fork-join-executor" section could only set to "FIFO" or "LIFO".""")
    }

    new ForkJoinExecutorServiceFactory(
      id,
      validate(tf),
      ThreadPoolConfig.scaledPoolSize(
        config.getInt("parallelism-min"),
        config.getDouble("parallelism-factor"),
        config.getInt("parallelism-max")),
      asyncMode,
      config.getInt("maximum-pool-size"),
      config.getBoolean("virtualize"))
  }
}
