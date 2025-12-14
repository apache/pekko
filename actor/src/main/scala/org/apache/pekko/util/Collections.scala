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

package org.apache.pekko.util

import scala.annotation.tailrec
import scala.collection.immutable

/**
 * INTERNAL API
 */
private[pekko] object Collections {
  // Cached function that can be used with PartialFunction.applyOrElse to ensure that A) the guard is only applied once,
  // and the caller can check the returned value with Collect.notApplied to query whether the PF was applied or not.
  // Prior art: https://github.com/scala/scala/blob/v2.11.4/src/library/scala/collection/immutable/List.scala#L458
  private object NotApplied extends (Any => Any) {
    final override def apply(v1: Any): Any = this
  }

  implicit class IterableOps[T](val iterable: java.lang.Iterable[T]) extends AnyVal {
    def collectToImmutableSeq[R](pf: PartialFunction[T, R]): immutable.Seq[R] = {
      val builder = immutable.Seq.newBuilder[R]
      iterable.forEach((t: T) => {
        // 1. `applyOrElse` is faster than (`pf.isDefinedAt` and then `pf.apply`)
        // 2. using reference comparing here instead of pattern matching can generate less and quicker bytecode,
        //   eg: just a simple `IF_ACMPNE`, and you can find the same trick in `CollectWhile` operator.
        //   If you interest, you can check the associated PR for this change and the
        //   current implementation of `scala.collection.IterableOnceOps.collectFirst`.
        pf.applyOrElse(t, NotApplied) match {
          case _: NotApplied.type => // do nothing
          case r: R @unchecked    => builder += r
        }
      })
      builder.result()
    }
  }

  case object EmptyImmutableSeq extends immutable.Seq[Nothing] {
    override final def iterator = Iterator.empty
    override final def apply(idx: Int): Nothing = throw new java.lang.IndexOutOfBoundsException(idx.toString)
    override final def length: Int = 0
  }

  abstract class PartialImmutableValuesIterable[From, To] extends immutable.Iterable[To] {
    def isDefinedAt(from: From): Boolean
    def apply(from: From): To
    def valuesIterator: Iterator[From]
    final def iterator: Iterator[To] = {
      val superIterator = valuesIterator
      new Iterator[To] {
        private[this] var _next: To = _
        private[this] var _hasNext = false

        override final def hasNext: Boolean = {
          @tailrec def tailrecHasNext(): Boolean = {
            if (!_hasNext && superIterator.hasNext) { // If we need and are able to look for the next value
              val potentiallyNext = superIterator.next()
              if (isDefinedAt(potentiallyNext)) {
                _next = apply(potentiallyNext)
                _hasNext = true
                true
              } else tailrecHasNext() // Attempt to find the next
            } else _hasNext // Return if we found one
          }

          tailrecHasNext()
        }

        override final def next(): To =
          if (hasNext) {
            val ret = _next
            _next = null.asInstanceOf[To] // Mark as consumed (nice to the GC, don't leak the last returned value)
            _hasNext = false // Mark as consumed (we need to look for the next value)
            ret
          } else throw new java.util.NoSuchElementException("next")
      }
    }

    override lazy val size: Int = iterator.size
    override def foreach[C](f: To => C) = iterator.foreach(f)
  }

}
