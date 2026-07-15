/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit

import java.lang.StackWalker.StackFrame
import java.lang.reflect.Modifier
import java.util.stream.{ Stream => JStream }

import scala.util.matching.Regex

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object TestKitUtils {

  private val stackWalker: java.util.function.Function[JStream[StackFrame], Array[Class[?]]] =
    (frames: JStream[StackFrame]) =>
      frames.map(_.getDeclaringClass).toArray[Class[?]]((size: Int) => new Array[Class[?]](size))

  def testNameFromCallStack(classToStartFrom: Class[?], testKitRegex: Regex): String = {

    def isAbstractClass(clazz: Class[?]): Boolean = {
      try {
        Modifier.isAbstract(clazz.getModifiers)
      } catch {
        case _: Throwable => false // yes catch everything, best effort check
      }
    }

    val startFrom = classToStartFrom.getName
    val classes = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
      .walk(stackWalker)
    val filteredStack = classes.iterator
      // drop until we find the first occurrence of classToStartFrom
      .dropWhile(!_.getName.startsWith(startFrom))
      // then continue to the next entry after classToStartFrom that makes sense
      .dropWhile {
        case c if c.getName == startFrom                => true
        case c if c.getName.startsWith(startFrom + "$") => true // lambdas inside startFrom etc
        case c if testKitRegex.matches(c.getName)       => true // testkit internals
        case c if isAbstractClass(c)                    => true
        case _                                          => false
      }

    if (filteredStack.isEmpty)
      throw new IllegalArgumentException(s"Couldn't find [$startFrom] in call stack")

    // sanitize for actor system name
    scrubActorSystemName(filteredStack.next().getName)
  }

  /**
   * Sanitize the `name` to be used as valid actor system name by
   * replacing invalid characters. `name` may for example be a fully qualified
   * class name and then the short class name will be used.
   */
  def scrubActorSystemName(name: String): String = {
    name
      .replaceFirst("""^.*\.""", "") // drop package name
      .replaceAll("""\$\$?\w+""", "") // drop scala anonymous functions/classes
      .replaceAll("[^a-zA-Z_0-9-]", "_")
      .replaceAll("""MultiJvmNode\d+""", "") // drop MultiJvm suffix
  }
}
