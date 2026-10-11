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

  // just to get access to the class context, only used on Java 8 where StackWalker is not available
  private final class TrickySecurityManager extends SecurityManager {
    def getClassStack: Array[Class[_]] = getClassContext
  }

  // java.lang.StackWalker (Java 9+) is accessed through reflection since this module is compiled for Java 8.
  // SHOW_HIDDEN_FRAMES is needed to include the lambda classes, which SecurityManager.getClassContext
  // does not report on JDK 24+ (required by the lambda owner detection below for Scala 3)
  private val stackWalkerClassStack: OptionVal[() => Array[Class[_]]] =
    try {
      val walkerClass = Class.forName("java.lang.StackWalker")
      // nested class names are built with '$' + name to avoid Scala 2.12's missing interpolator lint
      val optionClass = Class.forName(walkerClass.getName + '$' + "Option")
      val getDeclaringClass = Class.forName(walkerClass.getName + '$' + "StackFrame").getMethod("getDeclaringClass")
      val options = new java.util.HashSet[AnyRef]()
      options.add(optionClass.getField("RETAIN_CLASS_REFERENCE").get(null))
      options.add(optionClass.getField("SHOW_HIDDEN_FRAMES").get(null))
      val walker = walkerClass.getMethod("getInstance", classOf[java.util.Set[_]]).invoke(null, options)
      val walk = walkerClass.getMethod("walk", classOf[java.util.function.Function[_, _]])
      val toClasses = new java.util.function.Function[java.util.stream.Stream[AnyRef], Array[Class[_]]] {
        override def apply(frames: java.util.stream.Stream[AnyRef]): Array[Class[_]] =
          frames.toArray.map(frame => getDeclaringClass.invoke(frame).asInstanceOf[Class[_]])
      }
      OptionVal.Some(() => walk.invoke(walker, toClasses).asInstanceOf[Array[Class[_]]])
    } catch {
      case NonFatal(_) => OptionVal.None
    }

  private def getClassStack: Array[Class[_]] = stackWalkerClassStack match {
    case OptionVal.Some(walk) =>
      // drop the reflection frames so that the stack starts at this class, like getClassContext
      val trace = walk()
      val start = trace.indexWhere(_ eq LoggerClass.getClass)
      if (start > 0) trace.drop(start) else trace
    case _ =>
      new TrickySecurityManager().getClassStack
  }

  private val defaultPrefixesToSkip = List("scala.runtime", "org.apache.pekko.actor.typed.internal")

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
