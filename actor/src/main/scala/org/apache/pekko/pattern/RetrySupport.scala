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

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor.Scheduler
import pekko.util.ConstantFun

/**
 * This trait provides the retry utility function
 */
trait RetrySupport {

  /**
   * Given a function from Unit to Future, returns an internally retrying Future.
   * The first attempt will be made immediately, each subsequent attempt will be made immediately
   * if the previous attempt failed.
   *
   * If attempts are exhausted the returned future is simply the result of invoking attempt.
   * Note that the attempt function will be invoked on the given execution context for subsequent
   * tries and therefore must be thread safe (i.e. not touch unsafe mutable state).
   *
   * <b>Example usage:</b>
   *
   * {{{
   * def possiblyFailing(): Future[Something] = ???
   * val withRetry: Future[Something] = retry(attempt = possiblyFailing, attempts = 10)
   * }}}
   */
  def retry[T](attempt: () => Future[T], attempts: Int)(implicit ec: ExecutionContext): Future[T] =
    RetrySupport.retry(attempt, attempts, attempted = 0)

  /**
   * Given a function from Unit to Future, returns an internally retrying Future.
   *
   * When the future is completed, the `shouldRetry` predicate is always been invoked with the result (or `null` if none)
   * and the exception (or `null` if none). If the `shouldRetry` predicate returns true, then a new attempt is made,
   * each subsequent attempt will be made after the 'delay' return by `delayFunction` (the input next attempt count start from 1).
   * Returns [[scala.None]] for no delay.
   *
   * If attempts are exhausted the returned future is simply the result of invoking attempt.
   * Note that the attempt function will be invoked on the given execution context for subsequent
   * tries and therefore must be thread safe (i.e. not touch unsafe mutable state).
   *
   * <b>Example usage:</b>
   *
   * {{{
   * def possiblyFailing(): Future[Something] = ???
   * val shouldRetry: (Something, Throwable) => throwable ne null
   * val withRetry: Future[Something] = retry(attempt = possiblyFailing, shouldRetry, attempts = 10)
   * }}}
   *
   * @param attempt     the function to be attempted
   * @param shouldRetry the predicate to determine if the attempt should be retried
   * @param attempts    the maximum number of attempts
   * @param ec          the execution context
   * @return the result future which maybe retried
   *
   * @since 1.1.0
   */
  def retry[T](attempt: () => Future[T],
      shouldRetry: (T, Throwable) => Boolean,
      attempts: Int)(implicit ec: ExecutionContext): Future[T] =
    RetrySupport.retry(attempt, shouldRetry, attempts, ConstantFun.scalaAnyToNone, attempted = 0)(ec, null)

  /**
   * Given a function from Unit to Future, returns an internally retrying Future.
   * The first attempt will be made immediately, each subsequent attempt will be made with a backoff time,
   * if the previous attempt failed.
   *
   * If attempts are exhausted the returned future is simply the result of invoking attempt.
   * Note that the attempt function will be invoked on the given execution context for subsequent
   * tries and therefore must be thread safe (i.e. not touch unsafe mutable state).
   *
   * <b>Example usage:</b>
   *
   * {{{
   * protected val sendAndReceive: HttpRequest => Future[HttpResponse]
   * private val sendReceiveRetry: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => retry[HttpResponse](
   *   attempt = () => sendAndReceive(req),
   *   attempts = 10,
   *   minBackoff = 1.seconds,
   *   maxBackoff = 2.seconds,
   *   randomFactor = 0.5
   * )
   * }}}
   *
   * @param minBackoff   minimum (initial) duration until the child actor will
   *                     started again, if it is terminated
   * @param maxBackoff   the exponential back-off is capped to this duration
   * @param randomFactor after calculation of the exponential back-off an additional
   *                     random delay based on this factor is added, e.g. `0.2` adds up to `20%` delay.
   *                     In order to skip this additional delay pass in `0`.
   */
  def retry[T](
      attempt: () => Future[T],
      attempts: Int,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double)(implicit ec: ExecutionContext, scheduler: Scheduler): Future[T] =
    retry(
      attempt,
      RetrySupport.retryOnException,
      attempts,
      minBackoff,
      maxBackoff,
      randomFactor)

  /**
   * Given a function from Unit to Future, returns an internally retrying Future.
   *
   * When the future is completed, the `shouldRetry` predicate is always been invoked with the result (or `null` if none)
   * and the exception (or `null` if none). If the `shouldRetry` predicate returns true, then a new attempt is made,
   * each subsequent attempt will be made after the 'delay' return by `delayFunction` (the input next attempt count start from 1).
   * Returns [[scala.None]] for no delay.
   *
   * If attempts are exhausted the returned future is simply the result of invoking attempt.
   * Note that the attempt function will be invoked on the given execution context for subsequent
   * tries and therefore must be thread safe (i.e. not touch unsafe mutable state).
   *
   * <b>Example usage:</b>
   *
   * {{{
   * protected val sendAndReceive: HttpRequest => Future[HttpResponse]
   * protected val shouldRetry: (HttpResponse, Throwable) => throwable ne null
   * private val sendReceiveRetry: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => retry[HttpResponse](
   *   attempt = () => sendAndReceive(req),
   *   shouldRetry,
   *   attempts = 10,
   *   minBackoff = 1.seconds,
   *   maxBackoff = 2.seconds,
   *   randomFactor = 0.5
   * )
   * }}}
   *
   * @param attempt     the function to be attempted
   * @param shouldRetry the predicate to determine if the attempt should be retried
   * @param attempts    the maximum number of attempts
   * @param minBackoff   minimum (initial) duration until the child actor will
   *                     started again, if it is terminated
   * @param maxBackoff   the exponential back-off is capped to this duration
   * @param randomFactor after calculation of the exponential back-off an additional
   *                     random delay based on this factor is added, e.g. `0.2` adds up to `20%` delay.
   *                     In order to skip this additional delay pass in `0`.
   * @param ec          the execution context
   * @param scheduler   the scheduler for scheduling a delay
   * @return the result future which maybe retried
   *
   * @since 1.1.0
   */
  def retry[T](
      attempt: () => Future[T],
      shouldRetry: (T, Throwable) => Boolean,
      attempts: Int,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double)(implicit ec: ExecutionContext, scheduler: Scheduler): Future[T] = {
    require(minBackoff != null, "Parameter minBackoff should not be null.")
    require(maxBackoff != null, "Parameter maxBackoff should not be null.")
    require(minBackoff > Duration.Zero, "Parameter minBackoff must be > 0")
    require(maxBackoff >= minBackoff, "Parameter maxBackoff must be >= minBackoff")
    require(0.0 <= randomFactor && randomFactor <= 1.0, "randomFactor must be between 0.0 and 1.0")
    retry(
      attempt,
      shouldRetry,
      attempts,
      attempted => Some(BackoffSupervisor.calculateDelay(attempted, minBackoff, maxBackoff, randomFactor)))
  }

  /**
   * Given a function from Unit to Future, returns an internally retrying Future.
   * The first attempt will be made immediately, each subsequent attempt will be made after 'delay'.
   * A scheduler (eg context.system.scheduler) must be provided to delay each retry.
   *
   * If attempts are exhausted the returned future is simply the result of invoking attempt.
   * Note that the attempt function will be invoked on the given execution context for subsequent
   * tries and therefore must be thread safe (i.e. not touch unsafe mutable state).
   *
   * <b>Example usage:</b>
   *
   * {{{
   * protected val sendAndReceive: HttpRequest => Future[HttpResponse]
   * private val sendReceiveRetry: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => retry[HttpResponse](
   *   attempt = () => sendAndReceive(req),
   *   attempts = 10,
   *   delay = 2.seconds
   * )
   * }}}
   */
  def retry[T](attempt: () => Future[T], attempts: Int, delay: FiniteDuration)(
      implicit ec: ExecutionContext,
      scheduler: Scheduler): Future[T] =
    retry(attempt, attempts, _ => Some(delay))

  /**
   * Given a function from Unit to Future, returns an internally retrying Future.
   *
   * When the future is completed, the `shouldRetry` predicate is always been invoked with the result (or `null` if none)
   * and the exception (or `null` if none). If the `shouldRetry` predicate returns true, then a new attempt is made,
   * each subsequent attempt will be made after the 'delay' return by `delayFunction` (the input next attempt count start from 1).
   * Returns [[scala.None]] for no delay.
   *
   * If attempts are exhausted the returned future is simply the result of invoking attempt.
   * Note that the attempt function will be invoked on the given execution context for subsequent
   * tries and therefore must be thread safe (i.e. not touch unsafe mutable state).
   *
   * <b>Example usage:</b>
   *
   * {{{
   * protected val sendAndReceive: HttpRequest => Future[HttpResponse]
   * protected val shouldRetry: (HttpResponse, Throwable) => throwable ne null
   * private val sendReceiveRetry: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => retry[HttpResponse](
   *   attempt = () => sendAndReceive(req),
   *   shouldRetry,
   *   attempts = 10,
   *   delay = 2.seconds
   * )
   * }}}
   *
   * @param attempt     the function to be attempted
   * @param shouldRetry the predicate to determine if the attempt should be retried
   * @param attempts    the maximum number of attempts
   * @param delay       the delay duration
   * @param ec          the execution context
   * @param scheduler   the scheduler for scheduling a delay
   * @return the result future which maybe retried
   *
   * @since 1.1.0
   */
  def retry[T](attempt: () => Future[T],
      shouldRetry: (T, Throwable) => Boolean,
      attempts: Int,
      delay: FiniteDuration)(
      implicit ec: ExecutionContext,
      scheduler: Scheduler): Future[T] =
    retry(attempt, shouldRetry, attempts, _ => Some(delay))

  /**
   * Given a function from Unit to Future, returns an internally retrying Future.
   * The first attempt will be made immediately, each subsequent attempt will be made after
   * the 'delay' return by `delayFunction` (the input next attempt count start from 1).
   * Returns [[scala.None]] for no delay.
   *
   * A scheduler (eg context.system.scheduler) must be provided to delay each retry.
   * You could provide a function to generate the next delay duration after first attempt,
   * this function should never return `null`, otherwise an [[java.lang.IllegalArgumentException]] will be through.
   *
   * If attempts are exhausted the returned future is simply the result of invoking attempt.
   * Note that the attempt function will be invoked on the given execution context for subsequent
   * tries and therefore must be thread safe (i.e. not touch unsafe mutable state).
   *
   * <b>Example usage:</b>
   *
   * //retry with back off
   * {{{
   * protected val sendAndReceive: HttpRequest => Future[HttpResponse]
   * private val sendReceiveRetry: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => retry[HttpResponse](
   *   attempt = () => sendAndReceive(req),
   *   attempts = 10,
   *   delayFunction = attempted => Option(2.seconds * attempted)
   * )
   * }}}
   */
  def retry[T](attempt: () => Future[T], attempts: Int, delayFunction: Int => Option[FiniteDuration])(
      implicit
      ec: ExecutionContext,
      scheduler: Scheduler): Future[T] =
    RetrySupport.retry(attempt, RetrySupport.retryOnException, attempts, delayFunction, attempted = 0)

  /**
   * Given a function from Unit to Future, returns an internally retrying Future.
   *
   * When the future is completed, the `shouldRetry` predicate is always been invoked with the result (or `null` if none)
   * and the exception (or `null` if none). If the `shouldRetry` predicate returns true, then a new attempt is made,
   * each subsequent attempt will be made after the 'delay' return by `delayFunction` (the input next attempt count start from 1).
   * Returns [[scala.None]] for no delay.
   *
   * A scheduler (eg context.system.scheduler) must be provided to delay each retry.
   * You could provide a function to generate the next delay duration after first attempt,
   * this function should never return `null`, otherwise an [[java.lang.IllegalArgumentException]] will be through.
   *
   * If attempts are exhausted the returned future is simply the result of invoking attempt.
   * Note that the attempt function will be invoked on the given execution context for subsequent
   * tries and therefore must be thread safe (i.e. not touch unsafe mutable state).
   *
   * <b>Example usage:</b>
   *
   * //retry with back off
   * {{{
   * protected val sendAndReceive: HttpRequest => Future[HttpResponse]
   * protected val shouldRetry: (HttpResponse, Throwable) => throwable ne null
   * private val sendReceiveRetry: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => retry[HttpResponse](
   *   attempt = () => sendAndReceive(req),
   *   shouldRetry,
   *   attempts = 10,
   *   delayFunction = attempted => Option(2.seconds * attempted)
   * )
   * }}}
   *
   * @param attempt     the function to be attempted
   * @param shouldRetry the predicate to determine if the attempt should be retried
   * @param attempts    the maximum number of attempts
   * @param delayFunction the function to generate the next delay duration, `None` for no delay
   * @param ec          the execution context
   * @param scheduler   the scheduler for scheduling a delay
   * @return the result future which maybe retried
   *
   * @since 1.1.0
   */
  def retry[T](attempt: () => Future[T],
      shouldRetry: (T, Throwable) => Boolean,
      attempts: Int,
      delayFunction: Int => Option[FiniteDuration])(implicit ec: ExecutionContext, scheduler: Scheduler): Future[T] =
    RetrySupport.retry(attempt, shouldRetry, attempts, delayFunction, attempted = 0)
}

object RetrySupport extends RetrySupport {
  private val retryOnException: (Any, Throwable) => Boolean = (_: Any, e: Throwable) => e != null

  private def retry[T](attempt: () => Future[T], maxAttempts: Int, attempted: Int)(
      implicit ec: ExecutionContext): Future[T] =
    retry(attempt, retryOnException, maxAttempts, ConstantFun.scalaAnyToNone, attempted)(ec, null)

  private def retry[T](
      attempt: () => Future[T],
      shouldRetry: (T, Throwable) => Boolean,
      maxAttempts: Int,
      delayFunction: Int => Option[FiniteDuration],
      attempted: Int)(implicit ec: ExecutionContext, scheduler: Scheduler): Future[T] = {
    def tryAttempt(): Future[T] =
      try
        attempt()
      catch {
        case NonFatal(exc) => Future.failed(exc) // in case the `attempt` function throws
      }

    def doRetry(nextAttempt: Int): Future[T] = delayFunction(nextAttempt) match {
      case Some(delay) =>
        if (delay.length < 1)
          retry(attempt, shouldRetry, maxAttempts, delayFunction, nextAttempt)
        else
          after(delay, scheduler) {
            retry(attempt, shouldRetry, maxAttempts, delayFunction, nextAttempt)
          }
      case None =>
        retry(attempt, shouldRetry, maxAttempts, delayFunction, nextAttempt)
      case null =>
        Future.failed(new IllegalArgumentException("The delayFunction of retry should not return null."))
    }

    require(attempt != null, "Parameter attempt should not be null.")
    require(maxAttempts >= 0, "Parameter maxAttempts must >= 0.")
    require(delayFunction != null, "Parameter delayFunction should not be null.")
    require(attempted >= 0, "Parameter attempted must >= 0.")

    if (maxAttempts - attempted > 0) {
      val result = tryAttempt()
      if (result eq null)
        result
      else {
        result.transformWith {
          case Success(value) if shouldRetry(value, null)                        => doRetry(attempted + 1)
          case Failure(e) if NonFatal(e) && shouldRetry(null.asInstanceOf[T], e) => doRetry(attempted + 1)
          case _                                                                 => result
        }
      }

    } else {
      tryAttempt()
    }
  }
}
