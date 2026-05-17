/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.impl.fusing

import scala.collection.immutable
import scala.util.Try

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Lightweight in-memory representations of value-presented `Source`s that can
 * be consumed without paying for substream materialization. Shared between
 * [[FlattenConcat]] and [[FlattenMerge]].
 */
@InternalApi
private[fusing] object InflightSources {

  /**
   * Common base. The optimized value-presented variants below have no
   * upstream to pull from or cancel, so `tryPull` / `cancel` / `materialize`
   * default to no-ops. Stages that wrap a real `SubSinkInlet` (for sources
   * that still require materialization) override these as needed.
   */
  private[fusing] abstract class InflightSource[T] {
    def hasNext: Boolean
    def next(): T
    def isClosed: Boolean
    def tryPull(): Unit = ()
    def cancel(cause: Throwable): Unit = ()
    def materialize(): Unit = ()
    def hasFailed: Boolean = failure.isDefined
    def failure: Option[Throwable] = None
  }

  private[fusing] final class InflightIteratorSource[T](iterator: Iterator[T]) extends InflightSource[T] {
    override def hasNext: Boolean = iterator.hasNext
    override def next(): T = iterator.next()
    override def isClosed: Boolean = !hasNext
  }

  private[fusing] final class InflightRangeSource[T](range: immutable.Range) extends InflightSource[T] {
    private val isEmptyRange = range.isEmpty
    private val rangeLast = if (isEmptyRange) 0 else range.last
    private val rangeStep = range.step
    private var nextElement = range.start
    private var closed = isEmptyRange

    override def hasNext: Boolean = !closed
    override def next(): T =
      if (closed) throw new NoSuchElementException("next called after completion")
      else {
        val current = nextElement
        if (current == rangeLast) closed = true
        else nextElement = current + rangeStep
        current.asInstanceOf[T]
      }
    override def isClosed: Boolean = closed
  }

  private[fusing] final class InflightRepeatSource[T](elem: T) extends InflightSource[T] {
    override def hasNext: Boolean = true
    override def next(): T = elem
    override def isClosed: Boolean = false
  }

  private[fusing] final class InflightCompletedFutureSource[T](result: Try[T]) extends InflightSource[T] {
    private var _hasNext = result.isSuccess
    override def hasNext: Boolean = _hasNext
    override def next(): T =
      if (_hasNext) {
        _hasNext = false
        result.get
      } else throw new NoSuchElementException("next called after completion")
    override def hasFailed: Boolean = result.isFailure
    override def failure: Option[Throwable] = result.failed.toOption
    // The future has already produced its value (or failure); the source is
    // fundamentally one-shot and reports as closed even before consumption.
    override def isClosed: Boolean = true
  }

  private[fusing] final class InflightPendingFutureSource[T](cb: InflightSource[T] => Unit)
      extends InflightSource[T]
      with (Try[T] => Unit) {
    private var result: Try[T] = MapAsync.NotYetThere
    private var consumed = false
    override def apply(result: Try[T]): Unit = {
      this.result = result
      cb(this)
    }
    override def hasNext: Boolean = (result ne MapAsync.NotYetThere) && !consumed && result.isSuccess
    override def next(): T =
      if (!consumed) {
        consumed = true
        result.get
      } else throw new NoSuchElementException("next called after completion")
    override def hasFailed: Boolean = (result ne MapAsync.NotYetThere) && result.isFailure
    override def failure: Option[Throwable] = if (result eq MapAsync.NotYetThere) None else result.failed.toOption
    override def isClosed: Boolean = consumed || hasFailed
  }
}
