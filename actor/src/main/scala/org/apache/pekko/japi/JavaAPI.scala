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

package org.apache.pekko.japi

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.runtime.AbstractPartialFunction
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.util.Collections.EmptyImmutableSeq

/**
 * Java API
 * Represents a pair (tuple) of two elements.
 *
 * Additional tuple types for 3 to 22 values are defined in the `org.apache.pekko.japi.tuple` package, e.g. [[pekko.japi.tuple.Tuple3]].
 */
@SerialVersionUID(1L)
case class Pair[A, B](first: A, second: B) {
  def toScala: (A, B) = (first, second)
}
object Pair {
  def create[A, B](first: A, second: B): Pair[A, B] = new Pair(first, second)
}

object JavaPartialFunction {
  sealed abstract class NoMatchException extends RuntimeException with NoStackTrace
  case object NoMatch extends NoMatchException
  final def noMatch(): RuntimeException = NoMatch
}

/**
 * Helper for implementing a *pure* partial function: it will possibly be
 * invoked multiple times for a single “application”, because its only abstract
 * method is used for both isDefinedAt() and apply(); the former is mapped to
 * `isCheck == true` and the latter to `isCheck == false` for those cases where
 * this is important to know.
 *
 * Failure to match is signaled by throwing `noMatch()`, i.e. not returning
 * normally (the exception used in this case is pre-allocated, hence not
 * <i>that</i> expensive).
 *
 * {{{
 * new JavaPartialFunction<Object, String>() {
 *   public String apply(Object in, boolean isCheck) {
 *     if (in instanceof TheThing) {
 *       if (isCheck) return null; // to spare the expensive or side-effecting code
 *       return doSomethingWithTheThing((TheThing) in);
 *     } else {
 *       throw noMatch();
 *     }
 *   }
 * }
 * }}}
 *
 * The typical use of partial functions from Apache Pekko looks like the following:
 *
 * {{{
 * if (pf.isDefinedAt(x)) {
 *   pf.apply(x);
 * }
 * }}}
 *
 * i.e. it will first call `JavaPartialFunction.apply(x, true)` and if that
 * does not throw `noMatch()` it will continue with calling
 * `JavaPartialFunction.apply(x, false)`.
 */
abstract class JavaPartialFunction[A, B] extends AbstractPartialFunction[A, B] {
  import JavaPartialFunction._

  @throws(classOf[Exception])
  def apply(x: A, isCheck: Boolean): B

  final def isDefinedAt(x: A): Boolean =
    try {
      apply(x, true); true
    } catch { case NoMatch => false }
  final override def apply(x: A): B =
    try apply(x, false)
    catch { case NoMatch => throw new MatchError(x) }
  final override def applyOrElse[A1 <: A, B1 >: B](x: A1, default: A1 => B1): B1 =
    try apply(x, false)
    catch { case NoMatch => default(x) }
}

/**
 * This class hold common utilities for Java
 */
object Util {

  /**
   * Returns a ClassTag describing the provided Class.
   */
  def classTag[T](clazz: Class[T]): ClassTag[T] = ClassTag(clazz)

  /**
   * Returns an immutable.Seq representing the provided array of Classes,
   * an overloading of the generic immutableSeq in Util, to accommodate for erasure.
   */
  def immutableSeq(arr: Array[Class[_]]): immutable.Seq[Class[_]] = immutableSeq[Class[_]](arr)

  /**
   * Turns an array into an immutable Scala sequence (by copying it).
   */
  def immutableSeq[T](arr: Array[T]): immutable.Seq[T] =
    if ((arr ne null) && arr.length > 0) arr.toIndexedSeq else Nil

  /**
   * Turns an [[java.lang.Iterable]] into an immutable Scala sequence (by copying it).
   */
  def immutableSeq[T](iterable: java.lang.Iterable[T]): immutable.Seq[T] =
    iterable match {
      case imm: immutable.Seq[_] => imm.asInstanceOf[immutable.Seq[T]]
      case other                 =>
        val i = other.iterator()
        if (i.hasNext) {
          val builder = new immutable.VectorBuilder[T]

          while ({ builder += i.next(); i.hasNext }) ()

          builder.result()
        } else EmptyImmutableSeq
    }

  def immutableSingletonSeq[T](value: T): immutable.Seq[T] = value :: Nil

  def javaArrayList[T](seq: Seq[T]): java.util.List[T] = {
    val size = seq.size
    val l = new java.util.ArrayList[T](size)
    seq.foreach(l.add) // TODO could be optimised based on type of Seq
    l
  }

  /**
   * Turns an [[java.lang.Iterable]] into an immutable Scala IndexedSeq (by copying it).
   */
  def immutableIndexedSeq[T](iterable: java.lang.Iterable[T]): immutable.IndexedSeq[T] =
    immutableSeq(iterable).toVector

  // TODO in case we decide to pull in scala-java8-compat methods below could be removed - https://github.com/akka/akka/issues/16247

  def option[T](jOption: java.util.Optional[T]): scala.Option[T] =
    scala.Option(jOption.orElse(null.asInstanceOf[T]))
}
