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

package org.apache.pekko.pattern

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor._
import pekko.dispatch.Futures

trait FutureTimeoutSupport {

  /**
   * Returns a [[scala.concurrent.Future]] that will be completed with the success or failure of the provided value
   * after the specified duration.
   */
  def after[T](duration: FiniteDuration)(value: => Future[T])(
      implicit system: ClassicActorSystemProvider): Future[T] = {
    after(duration, using = system.classicSystem.scheduler)(value)(system.classicSystem.dispatcher)
  }

  /**
   * Returns a [[java.util.concurrent.CompletionStage]] that will be completed with the success or failure of the provided value
   * after the specified duration.
   */
  def afterCompletionStage[T](duration: FiniteDuration)(value: => CompletionStage[T])(
      implicit system: ClassicActorSystemProvider): CompletionStage[T] =
    afterCompletionStage(duration, system.classicSystem.scheduler)(value)(system.classicSystem.dispatcher)

  /**
   * Returns a [[scala.concurrent.Future]] that will be completed with the success or failure of the provided value
   * after the specified duration.
   */
  def after[T](duration: FiniteDuration, using: Scheduler)(value: => Future[T])(
      implicit ec: ExecutionContext): Future[T] =
    if (duration.isFinite && duration.length < 1) {
      try value
      catch { case NonFatal(t) => Future.failed(t) }
    } else {
      val p = Promise[T]()
      using.scheduleOnce(duration) {
        p.completeWith {
          try value
          catch { case NonFatal(t) => Future.failed(t) }
        }
      }
      p.future
    }

  /**
   * Returns a [[java.util.concurrent.CompletionStage]] that will be completed with the success or failure of the provided value
   * after the specified duration.
   */
  def afterCompletionStage[T](duration: FiniteDuration, using: Scheduler)(value: => CompletionStage[T])(
      implicit ec: ExecutionContext): CompletionStage[T] =
    if (duration.isFinite && duration.length < 1) {
      try value
      catch { case NonFatal(t) => Futures.failedCompletionStage(t) }
    } else {
      val p = new CompletableFuture[T]
      using.scheduleOnce(duration) {
        try {
          val future = value
          future.handle((t: T, ex: Throwable) => {
            if (t != null) p.complete(t)
            if (ex != null) p.completeExceptionally(ex)
          })
        } catch {
          case NonFatal(ex) => p.completeExceptionally(ex)
        }
      }
      p
    }
}
