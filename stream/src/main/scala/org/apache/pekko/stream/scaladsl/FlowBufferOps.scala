/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko.annotation.ApiMayChange

import scala.collection.immutable

/**
 * Additional buffer operations for Flow and Source.
 */
object FlowBufferOps {

  /**
   * Collect subsequent repetitions of an element (that is, if they arrive right after one another) into multiple
   * `List` buffers that will be emitted by the resulting Flow.
   *
   * @tparam T the element type
   */
  @ApiMayChange
  implicit class BufferUntilChangedOps[T, Mat](val flow: Flow[T, T, Mat]) extends AnyVal {
    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another) into multiple
     * `List` buffers that will be emitted by the resulting Flow.
     *
     * @return a Flow that buffers elements until they change
     */
    def bufferUntilChanged: Flow[T, immutable.Seq[T], Mat] = {
      flow.statefulMap(() => List.empty[T])(
        (buffer, element) =>
          buffer match {
            case head :: _ if head != element => (element :: Nil, buffer)
            case _                            => (element :: buffer, Nil)
          },
        buffer => Some(buffer))
        .filter(_.nonEmpty)
        .map(_.reverse)
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another), as compared by a key
     * extracted through the user provided `keySelector` function, into multiple `List` buffers that will be emitted by the
     * resulting Flow.
     *
     * @param keySelector function to compute comparison key for each element
     * @tparam K the key type
     * @return a Flow that buffers elements until they change based on the key
     */
    def bufferUntilChanged[K](keySelector: T => K): Flow[T, immutable.Seq[T], Mat] = {
      flow.statefulMap(() => (Option.empty[K], List.empty[T]))(
        (state, element) => {
          val (lastKeyOpt, buffer) = state
          val key = keySelector(element)
          lastKeyOpt match {
            case Some(lastKey) if lastKey != key => ((Some(key), element :: Nil), buffer)
            case _                               => ((Some(key), element :: buffer), Nil)
          }
        },
        state => Some(state._2))
        .filter(_.nonEmpty)
        .map(_.reverse)
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another), as compared by a key
     * extracted through the user provided `keySelector` function and compared using a supplied `keyComparator`, into multiple
     * `List` buffers that will be emitted by the resulting Flow.
     *
     * @param keySelector function to compute comparison key for each element
     * @param keyComparator predicate used to compare keys
     * @tparam K the key type
     * @return a Flow that buffers elements until they change based on the key and comparator
     */
    def bufferUntilChanged[K](keySelector: T => K, keyComparator: (K, K) => Boolean): Flow[T, immutable.Seq[T], Mat] = {
      flow.statefulMap(() => (Option.empty[K], List.empty[T]))(
        (state, element) => {
          val (lastKeyOpt, buffer) = state
          val key = keySelector(element)
          lastKeyOpt match {
            case Some(lastKey) if !keyComparator(lastKey, key) => ((Some(key), element :: Nil), buffer)
            case _                                             => ((Some(key), element :: buffer), Nil)
          }
        },
        state => Some(state._2))
        .filter(_.nonEmpty)
        .map(_.reverse)
    }
  }

  /**
   * Adds the bufferUntilChanged operation to Source.
   *
   * @tparam T the element type
   */
  @ApiMayChange
  implicit class SourceBufferUntilChangedOps[T, Mat](val source: Source[T, Mat]) extends AnyVal {
    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another) into multiple
     * `List` buffers that will be emitted by the resulting Source.
     *
     * @return a Source that buffers elements until they change
     */
    def bufferUntilChanged: Source[immutable.Seq[T], Mat] = {
      source.via(Flow[T].bufferUntilChanged)
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another), as compared by a key
     * extracted through the user provided `keySelector` function, into multiple `List` buffers that will be emitted by the
     * resulting Source.
     *
     * @param keySelector function to compute comparison key for each element
     * @tparam K the key type
     * @return a Source that buffers elements until they change based on the key
     */
    def bufferUntilChanged[K](keySelector: T => K): Source[immutable.Seq[T], Mat] = {
      source.via(Flow[T].bufferUntilChanged(keySelector))
    }

    /**
     * Collect subsequent repetitions of an element (that is, if they arrive right after one another), as compared by a key
     * extracted through the user provided `keySelector` function and compared using a supplied `keyComparator`, into multiple
     * `List` buffers that will be emitted by the resulting Source.
     *
     * @param keySelector function to compute comparison key for each element
     * @param keyComparator predicate used to compare keys
     * @tparam K the key type
     * @return a Source that buffers elements until they change based on the key and comparator
     */
    def bufferUntilChanged[K](keySelector: T => K, keyComparator: (K, K) => Boolean): Source[immutable.Seq[T], Mat] = {
      source.via(Flow[T].bufferUntilChanged(keySelector, keyComparator))
    }
  }
}
