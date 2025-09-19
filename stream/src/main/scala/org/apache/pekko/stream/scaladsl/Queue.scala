/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.annotation.InternalApi
import scala.concurrent.ExecutionContext
import pekko.stream.QueueOfferResult
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

/**
 * This trait allows to have a queue as a data source for some stream.
 */
trait SourceQueue[T] {

  /**
   * Offers an element to a stream and returns a [[Future]] that:
   * - completes with `Enqueued` if the element is consumed by a stream
   * - completes with `Dropped` when the stream dropped the offered element
   * - completes with `QueueClosed` when the stream is completed whilst the [[Future]] is active
   * - completes with `Failure(f)` in case of failure to enqueue element from upstream
   * - fails when stream is already completed
   *
   * Additionally when using the backpressure overflowStrategy:
   * - If the buffer is full the [[Future]] won't be completed until there is space in the buffer
   * - Calling offer before the [[Future]] is completed, in this case it will return a failed [[Future]]
   *
   * @param elem element to send to a stream
   */
  def offer(elem: T): Future[QueueOfferResult]

  /**
   * Returns a [[Future]] that will be completed if this operator
   * completes, or will be failed when the operator faces an internal failure.
   *
   * Note that this only means the elements have been passed downstream, not
   * that downstream has successfully processed them.
   */
  def watchCompletion(): Future[Done]
}

/**
 * This trait adds completion support to [[SourceQueue]].
 */
trait SourceQueueWithComplete[T] extends SourceQueue[T] {

  /**
   * Completes the stream normally. Use `watchCompletion` to be notified of this
   * operation’s success.
   *
   * Note that this only means the elements have been passed downstream, not
   * that downstream has successfully processed them.
   */
  def complete(): Unit

  /**
   * Completes the stream with a failure. Use `watchCompletion` to be notified of this
   * operation’s success.
   */
  def fail(ex: Throwable): Unit

  /**
   * Method returns a [[Future]] that will be completed if this operator
   * completes, or will be failed when the stream fails,
   * for example when [[SourceQueueWithComplete.fail]] is invoked.
   *
   * Note that this only means the elements have been passed downstream, not
   * that downstream has successfully processed them.
   */
  def watchCompletion(): Future[Done]
}

object SourceQueueWithComplete {
  final implicit class QueueOps[T](val queue: SourceQueueWithComplete[T]) extends AnyVal {
    // would have been better to add `asJava` in SourceQueueWithComplete trait, but not doing
    // that for backwards compatibility reasons

    /**
     * Converts the queue into a `javadsl.SourceQueueWithComplete`
     */
    def asJava: pekko.stream.javadsl.SourceQueueWithComplete[T] =
      SourceQueueWithComplete.asJava(queue)
  }

  /**
   * INTERNAL API: Converts the queue into a `javadsl.SourceQueueWithComplete`
   */
  @InternalApi private[pekko] def asJava[T](
      queue: SourceQueueWithComplete[T]): pekko.stream.javadsl.SourceQueueWithComplete[T] =
    new pekko.stream.javadsl.SourceQueueWithComplete[T] {
      def offer(elem: T): CompletionStage[QueueOfferResult] =
        queue.offer(elem).asJava
      def watchCompletion(): CompletionStage[Done] =
        queue.watchCompletion().asJava
      def complete(): Unit = queue.complete()
      def fail(ex: Throwable): Unit = queue.fail(ex)
    }
}

/**
 * This trait allows to have a queue as a sink for a stream.
 * A [[SinkQueue]] pulls data from a stream with a backpressure mechanism.
 */
trait SinkQueue[T] {

  /**
   * Pulls elements from the stream and returns a [[Future]] that:
   * - fails if the stream is failed
   * - completes with None in case the stream is completed
   * - completes with `Some(element)` in case the next element is available from stream.
   */
  def pull(): Future[Option[T]]
}

/**
 * This trait adds cancel support to [[SinkQueue]].
 */
trait SinkQueueWithCancel[T] extends SinkQueue[T] {

  /**
   * Cancels the stream. This method returns right away without waiting for actual finalizing the stream.
   */
  def cancel(): Unit
}

object SinkQueueWithCancel {
  final implicit class QueueOps[T](val queue: SinkQueueWithCancel[T]) extends AnyVal {
    // would have been better to add `asJava` in SinkQueueWithCancel trait, but not doing
    // that for backwards compatibility reasons

    def asJava: pekko.stream.javadsl.SinkQueueWithCancel[T] =
      SinkQueueWithCancel.asJava(queue)
  }

  /**
   * INTERNAL API: Converts the queue into a `javadsl.SinkQueueWithCancel`
   */
  @InternalApi private[pekko] def asJava[T](
      queue: SinkQueueWithCancel[T]): pekko.stream.javadsl.SinkQueueWithCancel[T] =
    new pekko.stream.javadsl.SinkQueueWithCancel[T] {
      override def pull(): CompletionStage[Optional[T]] =
        queue.pull().map(_.toJava)(ExecutionContext.parasitic).asJava
      override def cancel(): Unit = queue.cancel()
    }
}
