/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import org.apache.pekko
import pekko.annotation.DoNotInherit

/**
 * A queue of the given size that gives immediate feedback whether an element could be enqueued or not.
 *
 * Not for user extension
 */
@DoNotInherit
trait BoundedSourceQueue[T] {

  /**
   * Returns a [[pekko.stream.QueueOfferResult]] that notifies the caller if the element could be enqueued or not, or
   * the completion status of the queue.
   *
   * A result of `QueueOfferResult.Enqueued` does not guarantee that an element also has been or will be processed by
   * the downstream.
   */
  def offer(elem: T): QueueOfferResult

  /**
   * Completes the stream normally.
   */
  def complete(): Unit

  /**
   * Returns true if the stream has been completed, either normally or with failure.
   *
   * @since 1.1.0
   */
  def isCompleted: Boolean

  /**
   * Completes the stream with a failure.
   */
  def fail(ex: Throwable): Unit

  /**
   * Returns the approximate number of elements in this queue.
   */
  def size(): Int
}
