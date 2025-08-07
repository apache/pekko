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
import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.Try
import scala.util.control.NonFatal

import org.apache.pekko.annotation.InternalApi

/**
 * Collection of internal reflection utilities which may or may not be
 * available (most services specific to HotSpot, but fails gracefully).
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] object Reflect {

  /**
   * This optionally holds a function which looks N levels above itself
   * on the call stack and returns the `Class[_]` object for the code
   * executing in that stack frame. Implemented using
   * `sun.reflect.Reflection.getCallerClass` if available, None otherwise.
   *
   * Hint: when comparing to Thread.currentThread().getStackTrace, add two levels.
   */
  val getCallerClass: Option[Int => Class[_]] = {
    try {
      val c = Class.forName("sun.reflect.Reflection")
      val m = c.getMethod("getCallerClass", Array(classOf[Int]): _*)
      Some((i: Int) => m.invoke(null, Array[AnyRef](i.asInstanceOf[java.lang.Integer]): _*).asInstanceOf[Class[_]])
    } catch {
      case NonFatal(_) => None
    }
  }

  /**
   * INTERNAL API
   * @param clazz the class which to instantiate an instance of
   * @return a new instance from the default constructor of the given class
   */
  private[pekko] def instantiate[T](clazz: Class[T]): T = {
    val ctor = clazz.getDeclaredConstructor()
    try ctor.newInstance()
    catch {
      case _: IllegalAccessException =>
        ctor.setAccessible(true)
        ctor.newInstance()
    }
  }

  /**
   * INTERNAL API
   * Calls findConstructor and invokes it with the given arguments.
   */
  private[pekko] def instantiate[T](clazz: Class[T], args: immutable.Seq[Any]): T = {
    instantiate(findConstructor(clazz, args), args)
  }

  /**
   * INTERNAL API
   * Invokes the constructor with the given arguments.
   */
  private[pekko] def instantiate[T](constructor: Constructor[T], args: immutable.Seq[Any]): T = {
    constructor.setAccessible(true)
    try constructor.newInstance(args.asInstanceOf[Seq[AnyRef]]: _*)
    catch {
      case e: IllegalArgumentException =>
        val argString = args.map(safeGetClass).mkString("[", ", ", "]")
        throw new IllegalArgumentException(s"constructor $constructor is incompatible with arguments $argString", e)
    }
  }

  /**
   * INTERNAL API
   * Implements a primitive form of overload resolution a.k.a. finding the
   * right constructor.
   */
  private[pekko] def findConstructor[T](clazz: Class[T], args: immutable.Seq[Any]): Constructor[T] = {
    def error(msg: String): Nothing = {
      val argClasses = args.map(safeGetClass).mkString(", ")
      throw new IllegalArgumentException(s"$msg found on $clazz for arguments [$argClasses]")
    }

    val constructor: Constructor[T] =
      if (args.isEmpty) Try { clazz.getDeclaredConstructor() }.getOrElse(null)
      else {
        val length = args.length
        val candidates =
          clazz.getDeclaredConstructors.asInstanceOf[Array[Constructor[T]]].iterator.filter { c =>
            val parameterTypes = c.getParameterTypes
            parameterTypes.length == length &&
            (parameterTypes.iterator.zip(args.iterator).forall {
              case (found, required) =>
                found.isInstance(required) || BoxedType(found).isInstance(required) ||
                (required == null && !found.isPrimitive)
            })
          }
        if (candidates.hasNext) {
          val cstrtr = candidates.next()
          if (candidates.hasNext) error("multiple matching constructors")
          else cstrtr
        } else null
      }

    if (constructor eq null) error("no matching constructor")
    else constructor
  }

  private def safeGetClass(a: Any): Class[_] =
    if (a == null) classOf[AnyRef] else a.getClass

  /**
   * INTERNAL API
   * @param clazz the class which to instantiate an instance of
   * @return a function which when applied will create a new instance from the default constructor of the given class
   */
  private[pekko] def instantiator[T](clazz: Class[T]): () => T = () => instantiate(clazz)

  def findMarker(root: Class[_], marker: Class[_]): Type = {
    @tailrec def rec(curr: Class[_]): Type = {
      if ((curr.getSuperclass ne null) && marker.isAssignableFrom(curr.getSuperclass)) rec(curr.getSuperclass)
      else
        curr.getGenericInterfaces.collectFirst {
          case c: Class[_] if marker.isAssignableFrom(c)                                            => c
          case t: ParameterizedType if marker.isAssignableFrom(t.getRawType.asInstanceOf[Class[_]]) => t
        } match {
          case None                       => throw new IllegalArgumentException(s"cannot find [$marker] in ancestors of [$root]")
          case Some(c: Class[_])          => if (c == marker) c else rec(c)
          case Some(t: ParameterizedType) => if (t.getRawType == marker) t else rec(t.getRawType.asInstanceOf[Class[_]])
          case _                          => ??? // cannot happen due to collectFirst
        }
    }
    rec(root)
  }

  /**
   * INTERNAL API
   */
  private[pekko] def findClassLoader(): ClassLoader = {
    def findCaller(get: Int => Class[_]): ClassLoader =
      Iterator
        .from(2 /*is the magic number, promise*/ )
        .map(get)
        .dropWhile { c =>
          (c ne null) &&
          (c.getName.startsWith("org.apache.pekko.actor.ActorSystem") ||
          c.getName.startsWith("scala.Option") ||
          c.getName.startsWith("scala.collection.Iterator") ||
          c.getName.startsWith("org.apache.pekko.util.Reflect"))
        }
        .next() match {
        case null => getClass.getClassLoader
        case c    => c.getClassLoader
      }

    Option(Thread.currentThread().getContextClassLoader)
      .orElse(Reflect.getCallerClass.map(findCaller))
      .getOrElse(getClass.getClassLoader)
  }
}
