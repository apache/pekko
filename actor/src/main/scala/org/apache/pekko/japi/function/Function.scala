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

package org.apache.pekko.japi.function

import org.apache.pekko.util.ConstantFun

import scala.annotation.nowarn

/**
 * A Function interface. Used to create first-class-functions is Java.
 * `Serializable` is needed to be able to grab line number for Java 8 lambdas.
 * Supports throwing `Exception` in the apply, which the `java.util.function.Function` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait Function[-T, +R] extends java.io.Serializable {
  @throws(classOf[Exception])
  def apply(param: T): R

  /**
   * Creates a composed function that first applies `g` to its input, then applies this function to the result.
   * That is, the resulting function is equivalent to `this(g(x))`.
   * @since 2.0.0
   */
  def compose[V](g: Function[V, T]): Function[V, R] = (v: V) => this.apply(g.apply(v))

  /**
   * Creates a composed function that first applies this function to its input, then applies `g` to the result.
   * @since 2.0.0
   */
  def andThen[V](g: Function[R, V]): Function[T, V] = (t: T) => g.apply(this.apply(t))
}

object Function {

  /**
   * Returns a `Function` that always returns its input argument.
   * @since 1.2.0
   */
  def identity[T]: Function[T, T] = ConstantFun.javaIdentityFunction
}

/**
 * A Function interface. Used to create 2-arg first-class-functions is Java.
 * `Serializable` is needed to be able to grab line number for Java 8 lambdas.
 * Supports throwing `Exception` in the apply, which the `java.util.function.BiFunction` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait Function2[-T1, -T2, +R] extends java.io.Serializable {
  @throws(classOf[Exception])
  def apply(arg1: T1, arg2: T2): R

  /**
   * Compose this function with another function, such that the resulting function
   * is equivalent to `g(this(x1, x2))`.
   * @since 2.0.0
   */
  def andThen[V](g: Function[R, V]): Function2[T1, T2, V] =
    (t1: T1, t2: T2) => g.apply(this.apply(t1, t2))
}

/**
 * A Procedure is like a Function, but it doesn't produce a return value.
 * `Serializable` is needed to be able to grab line number for Java 8 lambdas.
 * Supports throwing `Exception` in the apply, which the `java.util.function.Consumer` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait Procedure[-T] extends java.io.Serializable {
  @throws(classOf[Exception])
  def apply(param: T): Unit
}

/**
 * An executable piece of code that takes no parameters and doesn't return any value.
 * `Serializable` is needed to be able to grab line number for Java 8 lambdas.
 * Supports throwing `Exception` in the apply, which the `java.util.function.Effect` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait Effect extends java.io.Serializable {

  @throws(classOf[Exception])
  def apply(): Unit
}

/**
 * Java API: Defines a criteria and determines whether the parameter meets this criteria.
 * `Serializable` is needed to be able to grab line number for Java 8 lambdas.
 * Supports throwing `Exception` in the apply, which the `java.util.function.Predicate` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait Predicate[-T] extends java.io.Serializable {
  def test(param: T): Boolean

  /**
   * Returns a predicate that represents the logical negation of this predicate.
   * @since 2.0.0
   */
  def negate: Predicate[T] = (t: T) => !this.test(t)
}

/**
 * Java API: Defines a criteria and determines whether the parameter meets this criteria.
 * `Serializable` is needed to be able to grab line number for Java 8 lambdas.
 * Supports throwing `Exception` in the apply, which the `java.util.function.BiPredicate` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait Predicate2[-T1, -T2] extends java.io.Serializable {
  def test(param1: T1, param2: T2): Boolean

  /**
   * Returns a predicate that represents the logical negation of this predicate.
   * @since 2.0.0
   */
  def negate: Predicate2[T1, T2] = (t1: T1, t2: T2) => !this.test(t1, t2)
}

/**
 * A constructor/factory, takes no parameters but creates a new value of type T every call.
 * Supports throwing `Exception` in the create method, which the `java.util.function.Supplier` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait Creator[+T] extends Serializable {

  /**
   * This method must return a different instance upon every call.
   */
  @throws(classOf[Exception])
  def create(): T
}
