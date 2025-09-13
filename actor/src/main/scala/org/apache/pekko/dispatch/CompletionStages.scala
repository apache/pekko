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

package org.apache.pekko.dispatch

import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ CompletableFuture, CompletionStage, Executor }
import java.util.function.{ BiConsumer, BiFunction }

import scala.annotation.nowarn

import org.apache.pekko

/**
 * CompletionStages provides utilities for working with `CompletionStage`s.
 */
object CompletionStages {

  /**
   * Convert a `CompletionStage` to a Scala `Future`.
   */
  def asScala[T](stage: CompletionStage[T]): scala.concurrent.Future[T] = {
    import org.apache.pekko.util.FutureConverters._
    stage.asScala
  }

  /**
   * Return the first `CompletionStage` in the given `Iterable` that matches the given predicate.
   * If no stage matches the predicate, an empty `Optional` is returned.
   *
   * @param stages   the stages to search through
   * @param predicate the predicate to apply to each completed stage
   * @return a `CompletionStage` that, when completed, will contain an `Optional` with the first matching element,
   *         or an empty `Optional` if none matched
   * @since 1.2.0
   */
  def find[T <: AnyRef](
      stages: java.lang.Iterable[_ <: CompletionStage[_ <: T]],
      predicate: pekko.japi.function.Function[T, java.lang.Boolean]
  ): CompletionStage[Optional[T]] = {
    def search(iterator: java.util.Iterator[_ <: CompletionStage[_ <: T]]): CompletionStage[Optional[T]] = {
      if (iterator.hasNext) {
        iterator.next().thenCompose { t =>
          if (predicate.apply(t)) {
            CompletableFuture.completedFuture(Optional.of(t))
          } else {
            search(iterator)
          }
        }
      } else {
        CompletableFuture.completedFuture(Optional.empty())
      }
    }
    search(stages.iterator())
  }

  /**
   * Returns a new `CompletionStage` that, when completed, will contain the result of the first
   * of the given stages to complete, with the same value or exception.
   *
   * If the given iterable is empty, the returned stage never completes.
   * @param stages the stages
   * @return a `CompletionStage` that, when completed, will contain the result of the first stage to complete
   *         with the same value or exception.
   * @since 1.2.0
   */
  def firstCompletedOf[T <: AnyRef](stages: java.lang.Iterable[_ <: CompletionStage[_ <: T]]): CompletionStage[T] = {
    val iterator = stages.iterator()
    if (!iterator.hasNext) {
      new CompletableFuture[T]() // never completes
    } else {
      val promise = new CompletableFuture[T]()
      val holder = new AtomicReference[CompletableFuture[T]](promise) with BiConsumer[T, Throwable] {
        override def accept(t: T, ex: Throwable): Unit = {
          val current = getAndSet(null)
          if (current ne null) {
            if (ex != null) current.completeExceptionally(ex)
            else current.complete(t)
          }
        }
      }
      while (iterator.hasNext && (holder.get() ne null)) {
        iterator.next().whenComplete(holder)
      }
      promise
    }
  }

  private def foldWithNext[T, R](
      iterator: java.util.Iterator[_ <: CompletionStage[_ <: T]],
      previous: R,
      function: pekko.japi.function.Function2[R, T, R]): CompletionStage[R] = {
    if (iterator.hasNext) {
      iterator.next().thenCompose { t =>
        val next = function.apply(previous, t)
        foldWithNext[T, R](iterator, next, function)
      }
    } else {
      CompletableFuture.completedFuture(previous)
    }
  }

  /**
   * Aggregate the results of the given stages using the given associative function and a zero value.
   * The stages are processed in the order they are given.
   *
   * @param zero     the zero value
   * @param stages   the stages to aggregate
   * @param function the associative function to use for aggregation
   * @return a `CompletionStage` that, when completed, will contain the aggregated result
   * @since 1.2.0
   */
  def fold[T <: AnyRef, R](
      zero: R,
      stages: java.lang.Iterable[_ <: CompletionStage[_ <: T]],
      function: pekko.japi.function.Function2[R, T, R]): CompletionStage[R] = {
    foldWithNext[T, R](stages.iterator(), zero, function)
  }

  /**
   * Reduce the results of the given stages using the given associative function.
   * The stages are processed in the order they are given.
   * If the given iterable is empty, the returned stage is completed with a `NoSuchElementException`.
   *
   * @param stages   the stages to reduce
   * @param function the associative function to use for reduction
   * @return a `CompletionStage` that, when completed, will contain the reduced result of the stages
   *         or a `NoSuchElementException` if the given iterable is empty
   * @since 1.2.0
   */
  @nowarn("msg=deprecated")
  def reduce[T <: AnyRef, R >: T](
      stages: java.lang.Iterable[_ <: CompletionStage[_ <: T]],
      function: pekko.japi.function.Function2[R, T, R]): CompletionStage[R] = {
    val iterator: java.util.Iterator[_ <: CompletionStage[_ <: T]] = stages.iterator()
    if (iterator.hasNext) {
      iterator.next().thenCompose { v => foldWithNext[T, R](iterator, v, function) }
    } else {
      Futures.failedCompletionStage(new NoSuchElementException("reduce of an empty iterable of CompletionStages"))
    }
  }

  private val AddToListFunction: BiFunction[java.util.List[Any], Any, java.util.List[Any]] = (list, elem) => {
    list.add(elem)
    list
  }

  private def addToListFunction[R](): BiFunction[java.util.List[R], R, java.util.List[R]] =
    AddToListFunction.asInstanceOf[BiFunction[java.util.List[R], R, java.util.List[R]]]

  private def combineNext[T](
      accumulate: CompletionStage[java.util.List[T]],
      next: CompletionStage[_ <: T],
      executor: Executor): CompletionStage[java.util.List[T]] = {
    if (executor eq null) {
      accumulate.thenCombine(next, addToListFunction())
    } else {
      accumulate.thenCombineAsync(next, addToListFunction(), executor)
    }
  }

  /**
   * Transform a `java.lang.Iterable` of `CompletionStage`s into a single `CompletionStage` with a `java.util.List`
   * of all the results.
   * The stages are processed in the order they are given.
   *
   * @param stages the stages to sequence
   * @param executor the executor to use for asynchronous execution, or `null` to use synchronous execution
   * @return a `CompletionStage` that, when completed, will contain a `java.util.List` with all the results
   * @since 1.2.0
   */
  def sequence[T](
      stages: java.lang.Iterable[_ <: CompletionStage[_ <: T]],
      executor: Executor): CompletionStage[java.util.List[T]] = {
    var result: CompletionStage[java.util.List[T]] = CompletableFuture.completedFuture(new java.util.ArrayList[T]())
    val iterator = stages.iterator()
    while (iterator.hasNext) {
      result = combineNext[T](result, iterator.next(), executor)
    }
    result
  }

  /**
   * Transform a `java.lang.Iterable` of `CompletionStage`s into a single `CompletionStage` with a `java.util.List`
   * of all the results.
   * The stages are processed in the order they are given.
   *
   * @param input the input iterable
   * @param function the function to apply to each element
   * @param executor the executor to use for asynchronous execution, or `null` to use synchronous execution
   * @return a `CompletionStage` that, when completed, will contain a `java.util.List` with all the results
   * @since 1.2.0
   */
  def traverse[T, R](
      input: java.lang.Iterable[T],
      function: pekko.japi.function.Function[T, CompletionStage[R]],
      executor: Executor): CompletionStage[java.util.List[R]] = {
    var result: CompletionStage[java.util.List[R]] = CompletableFuture.completedFuture(new java.util.ArrayList[R]())
    val iterator = input.iterator()
    while (iterator.hasNext) {
      result = combineNext[R](result, function.apply(iterator.next()), executor)
    }
    result
  }

}
