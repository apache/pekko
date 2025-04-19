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
trait Function[-T, +R] extends java.io.Serializable { outer =>
  @throws(classOf[Exception])
  def apply(param: T): R

  /** Returns a function that applies [fn] to the result of this function. */
  def andThen[U](fn: Function[R, U]): Function[T, U] = new Function[T,U] {
    override def apply(param: T) = fn(outer.apply(param))
  }

  /** Returns a Scala function representation for this function. */
  def toScala[T1 <: T, R1 >: R]: T1 => R1 = t => apply(t)
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

  def toScala[T1 <: T]: T1 => Unit = t => apply(t)
}

/**
 * A BiProcedure is like a BiFunction, but it doesn't produce a return value.
 * `Serializable` is needed to be able to grab line number for Java 8 lambdas.
 * Supports throwing `Exception` in the apply, which the `java.util.function.Consumer` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait BiProcedure[-T1,-T2] extends java.io.Serializable {
  @throws(classOf[Exception])
  def apply(t1: T1, t2: T2): Unit
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

  /** Returns a Scala function representation for this function. */
  def toScala: () => Unit = () => apply()
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
}

/**
 * A constructor/factory, takes no parameters but creates a new value of type T every call.
 * Supports throwing `Exception` in the apply, which the `java.util.function.Supplier` counterpart does not.
 */
@nowarn("msg=@SerialVersionUID has no effect")
@SerialVersionUID(1L)
@FunctionalInterface
trait Creator[+T] extends Serializable { outer =>

  /**
   * This method must return a different instance upon every call.
   */
  @throws(classOf[Exception])
  def create(): T

  /** Returns a function that applies [fn] to the result of this function. */
  def andThen[U](fn: Function[T, U]): Creator[U] = new Creator[U] {
    override def create() = fn(outer.create())
  }

  /** Returns a Scala function representation for this function. */
  def toScala[T1 >: T]: () => T1 = () => create()
}
