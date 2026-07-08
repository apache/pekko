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
import java.lang.StackWalker
import java.lang.invoke.{ MethodHandle, MethodHandles, MethodType }
import java.lang.reflect.{ Constructor, InvocationTargetException, Modifier, ParameterizedType, Type }

import scala.annotation.tailrec
import scala.collection.immutable
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
  private val lookup = MethodHandles.lookup()
  private val publicLookup = MethodHandles.publicLookup()
  private val noArgsInvokerType = MethodType.methodType(classOf[AnyRef])
  private val genericConstructorType = MethodType.methodType(classOf[AnyRef], classOf[Array[AnyRef]])
  private val invocationTargetExceptionConstructor =
    lookup.findConstructor(
      classOf[InvocationTargetException],
      MethodType.methodType(Void.TYPE, classOf[Throwable]))

  /**
   * This optionally holds a function which looks N levels above itself
   * on the call stack and returns the `Class[_]` object for the code
   * executing in that stack frame. Implemented using
   * `java.lang.StackWalker` (JDK 9+).
   *
   * Hint: when comparing to Thread.currentThread().getStackTrace, add two levels.
   */
  val getCallerClass: Option[Int => Class[?]] = {
    try {
      val walker = StackWalker.getInstance(
        java.util.Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE))
      Some((i: Int) =>
        walker.walk((s: java.util.stream.Stream[StackWalker.StackFrame]) =>
          s.skip(i.toLong).map[Class[?]](_.getDeclaringClass).findFirst().orElse(null)))
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
    val constructor = findConstructor(clazz, Nil)
    ensureInitialized(clazz)
    instantiate(noArgsConstructorInvoker(constructor))
  }

  /**
   * INTERNAL API
   * Calls findConstructor and invokes it with the given arguments.
   */
  private[pekko] def instantiate[T](clazz: Class[T], args: immutable.Seq[Any]): T = {
    val constructor = findConstructor(clazz, args)
    ensureInitialized(clazz)
    instantiate(constructorInvoker(constructor), argumentArray(args))
  }

  /**
   * INTERNAL API
   * Invokes the constructor with the given arguments.
   */
  private[pekko] def instantiate[T](constructor: MethodHandle, args: immutable.Seq[Any]): T = {
    ensureInitialized(constructor.`type`().returnType())
    instantiate(constructorInvoker(constructor), argumentArray(args))
  }

  /** INTERNAL API */
  private[pekko] def instantiate[T](constructorInvoker: MethodHandle, args: Array[AnyRef]): T = {
    val instance: AnyRef = constructorInvoker.invokeExact(args)
    instance.asInstanceOf[T]
  }

  /** INTERNAL API */
  private[pekko] def instantiate[T](constructorInvoker: MethodHandle): T = {
    val instance: AnyRef = constructorInvoker.invokeExact()
    instance.asInstanceOf[T]
  }

  /** INTERNAL API */
  private[pekko] def noArgsConstructorInvoker(constructor: MethodHandle): MethodHandle =
    wrapTargetException(constructor).asFixedArity().asType(noArgsInvokerType)

  /** INTERNAL API */
  private[pekko] def invokeStaticNoArg[T](
      clazz: Class[?],
      method: MethodHandle,
      callerLookup: MethodHandles.Lookup): T = {
    ensureInitialized(clazz, callerLookup)
    val invoker = wrapTargetException(method).asFixedArity().asType(noArgsInvokerType)
    val result: AnyRef = invoker.invokeExact()
    result.asInstanceOf[T]
  }

  /** INTERNAL API */
  private[pekko] def constructorInvoker(constructor: MethodHandle): MethodHandle =
    wrapTargetException(constructor)
      .asFixedArity()
      .asSpreader(classOf[Array[AnyRef]], constructor.`type`().parameterCount())
      .asType(genericConstructorType)

  /** INTERNAL API */
  private[pekko] def argumentArray(args: immutable.Seq[Any]): Array[AnyRef] =
    args.iterator.map(_.asInstanceOf[AnyRef]).toArray

  /**
   * INTERNAL API
   * Implements a primitive form of overload resolution a.k.a. finding the
   * right constructor.
   */
  private[pekko] def findConstructor[T](clazz: Class[T], args: immutable.Seq[Any]): MethodHandle = {
    def error(msg: String): Nothing = {
      val argClasses = args.map(safeGetClass).mkString(", ")
      throw new IllegalArgumentException(s"$msg found on $clazz for arguments [$argClasses]")
    }

    val selectedConstructorType =
      findConstructorMethodTypeFromClassMetadata(clazz, args).getOrElse(error("no matching constructor"))
    findConstructorHandle(clazz, selectedConstructorType)
  }

  private def findConstructorMethodTypeFromClassMetadata[T](clazz: Class[T], args: immutable.Seq[Any])
      : Option[MethodType] = {
    def matches(parameterTypes: Array[Class[?]]): Boolean =
      parameterTypes.length == args.length &&
      parameterTypes.iterator.zip(args.iterator).forall {
        case (found, required) =>
          found.isInstance(required) || BoxedType(found).isInstance(required) ||
          (required == null && !found.isPrimitive)
      }

    val candidates = clazz.getDeclaredConstructors.iterator
      .map(constructorMethodType)
      .filter(methodType => matches(methodType.parameterArray()))
      .toList
    candidates match {
      case single :: Nil => Some(single)
      case Nil           => None
      case _             =>
        val argClasses = args.map(safeGetClass).mkString(", ")
        throw new IllegalArgumentException(
          s"multiple matching constructors found on $clazz for arguments [$argClasses]")
    }
  }

  private def safeGetClass(a: Any): Class[?] =
    if (a == null) classOf[AnyRef] else a.getClass

  /**
   * INTERNAL API
   * @param clazz the class which to instantiate an instance of
   * @return a function which when applied will create a new instance from the default constructor of the given class
   */
  private[pekko] def instantiator[T](clazz: Class[T]): () => T = {
    lazy val constructor = noArgsConstructorInvoker(findConstructor(clazz, Nil))
    lazy val initialized: Unit = ensureInitialized(clazz)
    () => {
      val invoker = constructor
      initialized
      instantiate(invoker)
    }
  }

  def findMarker(root: Class[?], marker: Class[?]): Type = {
    @tailrec def rec(curr: Class[?]): Type = {
      if ((curr.getSuperclass ne null) && marker.isAssignableFrom(curr.getSuperclass)) rec(curr.getSuperclass)
      else
        curr.getGenericInterfaces.collectFirst {
          case c: Class[?] if marker.isAssignableFrom(c)                                            => c
          case t: ParameterizedType if marker.isAssignableFrom(t.getRawType.asInstanceOf[Class[?]]) => t
        } match {
          case None                       => throw new IllegalArgumentException(s"cannot find [$marker] in ancestors of [$root]")
          case Some(c: Class[?])          => if (c == marker) c else rec(c)
          case Some(t: ParameterizedType) => if (t.getRawType == marker) t else rec(t.getRawType.asInstanceOf[Class[?]])
          case _                          => ??? // cannot happen due to collectFirst
        }
    }
    rec(root)
  }

  private def constructorMethodType(constructor: Constructor[?]): MethodType =
    MethodType.methodType(Void.TYPE, constructor.getParameterTypes)

  private def findConstructorHandle[T](clazz: Class[T], methodType: MethodType): MethodHandle = {
    try lookup.findConstructor(clazz, methodType)
    catch {
      case _: IllegalAccessException =>
        try publicLookup.findConstructor(clazz, methodType)
        catch {
          case _: IllegalAccessException =>
            MethodHandles.privateLookupIn(clazz, lookup).findConstructor(clazz, methodType)
        }
    }
  }

  private def wrapTargetException(target: MethodHandle): MethodHandle = {
    val exceptionThrower =
      MethodHandles.throwException(target.`type`().returnType(), classOf[InvocationTargetException])
    val handler = MethodHandles.filterArguments(exceptionThrower, 0, invocationTargetExceptionConstructor)
    val handlerWithArguments = MethodHandles.dropArguments(handler, 1, target.`type`().parameterList())
    MethodHandles.catchException(target, classOf[Throwable], handlerWithArguments)
  }

  private[pekko] def ensureInitialized(clazz: Class[?]): Unit =
    ensureInitialized(clazz, lookup)

  private def ensureInitialized(clazz: Class[?], callerLookup: MethodHandles.Lookup): Unit = {
    try callerLookup.ensureInitialized(clazz)
    catch {
      case _: IllegalAccessException =>
        try publicLookup.ensureInitialized(clazz)
        catch {
          case _: IllegalAccessException =>
            MethodHandles.privateLookupIn(clazz, callerLookup).ensureInitialized(clazz)
        }
    }
  }

  private[pekko] def findStaticNoArgMethod(
      clazz: Class[?],
      methodName: String,
      callerLookup: MethodHandles.Lookup): MethodHandle =
    clazz.getDeclaredMethods.iterator
      .filter(method =>
        method.getName == methodName && Modifier.isStatic(method.getModifiers) && method.getParameterCount == 0)
      .toList match {
      case method :: Nil =>
        val methodType = MethodType.methodType(method.getReturnType)
        try callerLookup.findStatic(clazz, methodName, methodType)
        catch {
          case _: IllegalAccessException =>
            try publicLookup.findStatic(clazz, methodName, methodType)
            catch {
              case _: IllegalAccessException =>
                MethodHandles.privateLookupIn(clazz, callerLookup).findStatic(clazz, methodName, methodType)
            }
        }
      case Nil =>
        throw new NoSuchMethodException(s"${clazz.getName}.$methodName()")
      case _ =>
        throw new IllegalArgumentException(s"multiple static no-arg methods named [$methodName] found on $clazz")
    }

  /**
   * INTERNAL API
   */
  private[pekko] def findClassLoader(): ClassLoader = {
    def findCaller(get: Int => Class[?]): ClassLoader =
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
