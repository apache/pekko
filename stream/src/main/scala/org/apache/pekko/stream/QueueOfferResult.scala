/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import org.apache.pekko.annotation.DoNotInherit

/**
 * Not for user extension
 */
@DoNotInherit
sealed abstract class QueueOfferResult {

  /**
   * Return ture if the element was already enqueued, otherwise false.
   */
  def isEnqueued: Boolean
}

/**
 * Not for user extension
 */
@DoNotInherit
sealed abstract class QueueCompletionResult extends QueueOfferResult

/**
 * Contains types that is used as return types for streams Source queues
 */
object QueueOfferResult {

  /**
   * Type is used to indicate that stream is successfully enqueued an element
   */
  case object Enqueued extends QueueOfferResult {
    override def isEnqueued: Boolean = true
  }

  /**
   * Java API: The `Enqueued` singleton instance
   */
  def enqueued: QueueOfferResult = Enqueued

  /**
   * Type is used to indicate that stream is dropped an element
   */
  case object Dropped extends QueueOfferResult {
    override def isEnqueued: Boolean = false
  }

  /**
   * Java API: The `Dropped` singleton instance
   */
  def dropped: QueueOfferResult = Dropped

  /**
   * Type is used to indicate that stream is failed before or during call to the stream
   * @param cause - exception that stream failed with
   */
  final case class Failure(cause: Throwable) extends QueueCompletionResult {
    override def isEnqueued: Boolean = false
  }

  /**
   * Type is used to indicate that stream is completed before call
   */
  case object QueueClosed extends QueueCompletionResult {
    override def isEnqueued: Boolean = false
  }
}
