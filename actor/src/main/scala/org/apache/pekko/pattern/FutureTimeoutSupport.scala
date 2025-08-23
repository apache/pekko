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

package org.apache.pekko.pattern

import java.util.concurrent.{ CompletableFuture, CompletionStage, TimeoutException }

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor._

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
  def afterCompletionStage[T](duration: java.time.Duration)(value: => CompletionStage[T])(
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
  def afterCompletionStage[T](duration: java.time.Duration, using: Scheduler)(value: => CompletionStage[T])(
      implicit ec: ExecutionContext): CompletionStage[T] =
    if (duration.isZero || duration.isNegative) {
      try value
      catch { case NonFatal(t) => CompletableFuture.failedStage(t) }
    } else {
      val p = new CompletableFuture[T]
      using.scheduleOnce(
        duration,
        new Runnable {
          override def run(): Unit =
            try {
              val future = value
              future.handle[Unit]((t: T, ex: Throwable) => {
                if (t != null) p.complete(t)
                if (ex ne null) p.completeExceptionally(ex)
              })
            } catch {
              case NonFatal(ex) => p.completeExceptionally(ex)
            }
        })
      p
    }

  /**
   * Returns a [[scala.concurrent.Future]] that will be completed with a [[TimeoutException]]
   * if the provided value is not completed within the specified duration.
   * @since 1.2.0
   */
  def timeout[T](duration: FiniteDuration, using: Scheduler)(value: => Future[T])(
      implicit ec: ExecutionContext): Future[T] = {
    val future =
      try value
      catch {
        case NonFatal(t) => Future.failed(t)
      }
    future.value match {
      case Some(_) => future
      case None    => // not completed yet
        val p = Promise[T]()
        val timeout = using.scheduleOnce(duration) {
          p.tryFailure(new TimeoutException(s"Timeout of $duration expired"))
          if (future.isInstanceOf[CompletableFuture[T @unchecked]]) {
            future.asInstanceOf[CompletableFuture[T]]
              .toCompletableFuture
              .cancel(true)
          }
        }
        future.onComplete { result =>
          timeout.cancel()
          p.tryComplete(result)
        }(pekko.dispatch.ExecutionContexts.parasitic)
        p.future
    }
  }

  /**
   * Returns a [[java.util.concurrent.CompletionStage]] that will be completed with a [[TimeoutException]]
   * if the provided value is not completed within the specified duration.
   * @since 1.2.0
   */
  @deprecated("Use `CompletableFuture#orTimeout instead.", "2.0.0")
  def timeoutCompletionStage[T](duration: java.time.Duration, using: Scheduler)(value: => CompletionStage[T])(
      implicit ec: ExecutionContext): CompletionStage[T] = {
    val stage: CompletionStage[T] =
      try value
      catch {
        case NonFatal(t) => CompletableFuture.failedStage(t)
      }
    if (stage.toCompletableFuture.isDone) {
      stage
    } else {
      val p = new CompletableFuture[T]
      val timeout = using.scheduleOnce(duration,
        () => {
          p.completeExceptionally(new TimeoutException(s"Timeout of $duration expired"))
          stage.toCompletableFuture.cancel(true)
          ()
        })
      stage.handle[Unit]((v: T, ex: Throwable) => {
        timeout.cancel()
        if (v != null) p.complete(v)
        if (ex ne null) p.completeExceptionally(ex)
      })
      p
    }
  }

}
