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

import scala.util.control.NonFatal

/**
 * Helper class for determining whether a `Throwable` is fatal or not.
 * User should only catch the non-fatal one,and keep rethrow the fatal one.
 *
 * Fatal errors are errors like `VirtualMachineError`
 * (for example, `OutOfMemoryError` and `StackOverflowError`, subclasses of `VirtualMachineError`), `ThreadDeath`,
 * `LinkageError`, `InterruptedException`, `ControlThrowable`.
 *
 * Note. this helper keep the same semantic with `NonFatal` in Scala.
 * For example, all harmless `Throwable`s can be caught by:
 * {{{
 *   try {
 *     // dangerous stuff
 *   } catch(Throwable e) {
 *     if (Throwables.isNonFatal(e)){
 *       log.error(e, "Something not that bad.");
 *     } else {
 *       throw e;
 *     }
 * }}}
 */
object Throwables {

  /**
   * Returns true if the provided `Throwable` is to be considered non-fatal,
   * or false if it is to be considered fatal
   */
  def isNonFatal(throwable: Throwable): Boolean = NonFatal(throwable)

  /**
   * Returns true if the provided `Throwable` is to be considered fatal,
   * or false if it is to be considered non-fatal
   */
  def isFatal(throwable: Throwable): Boolean = !isNonFatal(throwable)

  /**
   * Throws the given `Throwable`, without requiring the caller to declare it in a `throws` clause.
   * @param t the `Throwable` to throw
   * @throws T the type of the `Throwable` to throw
   * @return never returns normally, but has return type `R` to allow usage in expressions
   * @since 2.0.0
   */
  def sneakyThrow[T <: Throwable, R](t: Throwable): R = {
    throw t.asInstanceOf[T]
  }
}
