/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.util.{ Failure, Success, Try }

import org.apache.pekko
import pekko.Done

/**
 * Holds a result of an IO operation.
 *
 * @param count Numeric value depending on context, for example IO operations performed or bytes processed.
 * @param status Status of the result. Can be either [[pekko.Done]] or an exception.
 */
final case class IOResult(
    count: Long,
    status: Try[Done]) {

  /**
   * Creates a new IOResult with the given count and the same status.
   */
  def withCount(value: Long): IOResult = copy(count = value)

  /**
   * Creates a new IOResult with the given status and the same count.
   */
  def withStatus(value: Try[Done]): IOResult = copy(status = value)

  /**
   * Java API: Numeric value depending on context, for example IO operations performed or bytes processed.
   */
  def getCount: Long = count

  /**
   * Java API: Indicates whether IO operation completed successfully or not.
   */
  def wasSuccessful: Boolean = status.isSuccess

  /**
   * Java API: If the IO operation resulted in an error, returns the corresponding [[java.lang.Throwable]]
   * or throws [[UnsupportedOperationException]] otherwise.
   */
  def getError: Throwable = status match {
    case Failure(t) => t
    case Success(_) => throw new UnsupportedOperationException("IO operation was successful.")
  }

}

object IOResult {

  def apply(count: Long): IOResult = IOResult(count, Success(Done))

  /** JAVA API: Creates successful IOResult */
  def createSuccessful(count: Long): IOResult =
    new IOResult(count, Success(Done))

  /** JAVA API: Creates failed IOResult, `count` should be the number of bytes (or other unit, please document in your APIs) processed before failing */
  def createFailed(count: Long, ex: Throwable): IOResult =
    new IOResult(count, Failure(ex))
}

/**
 * This exception signals that a stream has been completed or has an error while
 * there was still IO operations in progress
 *
 * @param count The number of bytes read/written up until the error
 * @param cause cause
 */
final class IOOperationIncompleteException(message: String, val count: Long, cause: Throwable)
    extends RuntimeException(message, cause) {

  def this(count: Long, cause: Throwable) =
    this(s"IO operation was stopped unexpectedly after $count bytes because of $cause", count, cause)

}
