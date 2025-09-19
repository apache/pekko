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

import java.util.concurrent.{ Callable, Executor, ExecutorService }
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, ExecutionContextExecutorService, Future, Promise }

import org.apache.pekko
import pekko.japi.function.Procedure

/**
 * ExecutionContexts is the Java API for ExecutionContexts
 */
object ExecutionContexts {

  /**
   * Returns a new ExecutionContextExecutor which will delegate execution to the underlying Executor,
   * and which will use the default error reporter.
   *
   * @param executor the Executor which will be used for the ExecutionContext
   * @return a new ExecutionContext
   */
  def fromExecutor(executor: Executor): ExecutionContextExecutor =
    ExecutionContext.fromExecutor(executor)

  /**
   * Returns a new ExecutionContextExecutor which will delegate execution to the underlying Executor,
   * and which will use the provided error reporter.
   *
   * @param executor the Executor which will be used for the ExecutionContext
   * @param errorReporter a Procedure that will log any exceptions passed to it
   * @return a new ExecutionContext
   */
  def fromExecutor(executor: Executor, errorReporter: Procedure[Throwable]): ExecutionContextExecutor =
    ExecutionContext.fromExecutor(executor, errorReporter.apply)

  /**
   * Returns a new ExecutionContextExecutorService which will delegate execution to the underlying ExecutorService,
   * and which will use the default error reporter.
   *
   * @param executorService the ExecutorService which will be used for the ExecutionContext
   * @return a new ExecutionContext
   */
  def fromExecutorService(executorService: ExecutorService): ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(executorService)

  /**
   * Returns a new ExecutionContextExecutorService which will delegate execution to the underlying ExecutorService,
   * and which will use the provided error reporter.
   *
   * @param executorService the ExecutorService which will be used for the ExecutionContext
   * @param errorReporter a Procedure that will log any exceptions passed to it
   * @return a new ExecutionContext
   */
  def fromExecutorService(
      executorService: ExecutorService,
      errorReporter: Procedure[Throwable]): ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(executorService, errorReporter.apply)

  /**
   * @return a reference to the global ExecutionContext
   */
  def global(): ExecutionContextExecutor = ExecutionContext.global
}

/**
 * Futures is the Java API for Futures and Promises
 */
object Futures {

  /**
   * Convert a Scala Future to a Java CompletionStage.
   *
   * @since 1.2.0
   */
  def asJava[T](future: Future[T]): CompletionStage[T] = {
    import scala.jdk.FutureConverters._
    future.asJava
  }

  /**
   * Starts an asynchronous computation and returns a `Future` object with the result of that computation.
   *
   * The result becomes available once the asynchronous computation is completed.
   *
   * @param body     the asynchronous computation
   * @param executor the execution context on which the future is run
   * @return         the `Future` holding the result of the computation
   */
  def future[T](body: Callable[T], executor: ExecutionContext): Future[T] = Future(body.call)(executor)

  /**
   * Creates a promise object which can be completed with a value.
   *
   * @return         the newly created `Promise` object
   */
  def promise[T](): Promise[T] = Promise[T]()

  /**
   * creates an already completed Promise with the specified exception
   */
  def failed[T](exception: Throwable): Future[T] = Future.failed(exception)

  /**
   * Creates an already completed Promise with the specified result
   */
  def successful[T](result: T): Future[T] = Future.successful(result)

  /**
   * Creates an already completed CompletionStage with the specified exception
   */
  @deprecated("Use `CompletableFuture#failedStage` instead.", since = "2.0.0")
  def failedCompletionStage[T](ex: Throwable): CompletionStage[T] = {
    val f = CompletableFuture.completedFuture[T](null.asInstanceOf[T])
    f.obtrudeException(ex)
    f
  }
}
