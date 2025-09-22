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

package org.apache.pekko.actor.typed.internal

import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object LoggerClass {

  private val defaultPrefixesToSkip = List("scala.runtime", "org.apache.pekko.actor.typed.internal")
  private val OPTIONS: java.util.Set[StackWalker.Option] = java.util.Set.of(
    StackWalker.Option.RETAIN_CLASS_REFERENCE, StackWalker.Option.SHOW_HIDDEN_FRAMES)
  private val CLASS_STACK_WALKER: java.util.function.Function[
    java.util.stream.Stream[StackWalker.StackFrame], Array[Class[_]]] =
    (frames: java.util.stream.Stream[StackWalker.StackFrame]) =>
      frames.map(frame => frame.getDeclaringClass)
        .toArray[Class[_]]((size: Int) => new Array[Class[_]](size))

  private def getClassStack: Array[Class[_]] = StackWalker.getInstance(OPTIONS).walk(CLASS_STACK_WALKER)

  /**
   * Try to extract a logger class from the call stack, if not possible the provided default is used
   */
  def detectLoggerClassFromStack(default: Class[_], additionalPrefixesToSkip: List[String] = Nil): Class[_] = {
    try {
      def skip(name: String): Boolean = {
        def loop(skipList: List[String]): Boolean = skipList match {
          case Nil          => false
          case head :: tail =>
            if (name.startsWith(head)) true
            else loop(tail)
        }

        loop(additionalPrefixesToSkip ::: defaultPrefixesToSkip)
      }

      val trace = getClassStack
      var suitableClass: OptionVal[Class[_]] = OptionVal.None
      var idx = 1 // skip this method/class and right away
      while (suitableClass.isEmpty && idx < trace.length) {
        val clazz = trace(idx)
        val name = clazz.getName
        if (!skip(name)) suitableClass = OptionVal.Some(clazz)
        idx += 1
      }
      suitableClass match {
        case OptionVal.Some(cls) =>
          // Fix start
          val lambdaClsOwner = for {
            nextCaller <- trace.lift(idx)
            nextName = nextCaller.getName()
            lambdaNameIdx = nextName.indexOf("$$Lambda")
            if nextName.startsWith(cls.getName()) && lambdaNameIdx > 0
            lambdaClsOwner = nextName.substring(0, lambdaNameIdx)
          } yield Class.forName(lambdaClsOwner)
          // TODO: can potentially guard for ClassNotFoundException, but seems unlikely
          lambdaClsOwner.getOrElse(cls)
        case _ =>
          default
      }
    } catch {
      case NonFatal(_) => default
    }
  }

}
