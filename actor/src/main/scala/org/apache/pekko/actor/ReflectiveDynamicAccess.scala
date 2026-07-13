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

package org.apache.pekko.actor

import java.lang.invoke.{ MethodHandle, MethodHandles, MethodType, VarHandle }
import java.util.concurrent.ConcurrentHashMap

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Try

import org.apache.pekko
import pekko.annotation.DoNotInherit

/**
 * This is the default [[pekko.actor.DynamicAccess]] implementation used by [[pekko.actor.ExtendedActorSystem]]
 * unless overridden. It uses reflection to turn fully-qualified class names into `Class[_]` objects
 * and creates instances from there using constructor method handles. The class loader
 * to be used for all this is determined by the actor system’s class loader by default.
 *
 * Not for user extension or construction
 */
@DoNotInherit
class ReflectiveDynamicAccess(val classLoader: ClassLoader) extends DynamicAccess {
  private val lookup = MethodHandles.lookup()
  private val publicLookup = MethodHandles.publicLookup()
  private val genericConstructorType = MethodType.methodType(classOf[AnyRef], classOf[Array[AnyRef]])
  private val constructorHandles = new ConcurrentHashMap[Class[?], ConcurrentHashMap[MethodType, MethodHandle]]
  private val moduleHandles = new ConcurrentHashMap[Class[?], VarHandle]

  override def getClassFor[T: ClassTag](fqcn: String): Try[Class[? <: T]] =
    Try[Class[? <: T]] {
      val c = Class.forName(fqcn, false, classLoader).asInstanceOf[Class[? <: T]]
      val t = implicitly[ClassTag[T]].runtimeClass
      if (t.isAssignableFrom(c)) c else throw new ClassCastException(t.toString + " is not assignable from " + c)
    }

  override def createInstanceFor[T: ClassTag](clazz: Class[?], args: immutable.Seq[(Class[?], AnyRef)]): Try[T] =
    Try {
      val types = new Array[Class[?]](args.size)
      val values = new Array[AnyRef](args.size)
      val iterator = args.iterator
      var index = 0
      while (iterator.hasNext) {
        val argument: (Class[?], AnyRef) = iterator.next()
        types(index) = argument._1
        values(index) = argument._2
        index += 1
      }
      val methodType = MethodType.methodType(Void.TYPE, types)
      val handle = constructorHandle(clazz, methodType)
      val obj: AnyRef = handle.invokeExact(values)
      val t = implicitly[ClassTag[T]].runtimeClass
      if (t.isInstance(obj)) obj.asInstanceOf[T]
      else throw new ClassCastException(clazz.getName + " is not a subtype of " + t)
    }

  override def createInstanceFor[T: ClassTag](fqcn: String, args: immutable.Seq[(Class[?], AnyRef)]): Try[T] =
    getClassFor(fqcn).flatMap { c =>
      createInstanceFor(c, args)
    }

  override def classIsOnClasspath(fqcn: String): Boolean =
    getClassFor[Any](fqcn) match {
      case Failure(_: ClassNotFoundException | _: NoClassDefFoundError) =>
        false
      case _ =>
        true
    }

  override def getObjectFor[T: ClassTag](fqcn: String): Try[T] = {
    val classTry =
      if (fqcn.endsWith("$")) getClassFor(fqcn)
      else getClassFor(fqcn + "$").recoverWith { case _ => getClassFor(fqcn) }
    classTry.flatMap { c =>
      Try {
        val moduleHandle = moduleHandleFor(c)
        val t = implicitly[ClassTag[T]].runtimeClass
        moduleHandle.get() match {
          case null                  => throw new NullPointerException
          case x if !t.isInstance(x) => throw new ClassCastException(fqcn + " is not a subtype of " + t)
          case x: T                  => x
          case unexpected            =>
            throw new IllegalArgumentException(s"Unexpected module field: $unexpected") // will not happen, for exhaustiveness check
        }
      }
    }
  }

  private def constructorHandle(clazz: Class[?], methodType: MethodType): MethodHandle = {
    val existingByType = constructorHandles.get(clazz)
    val byType =
      if (existingByType ne null) existingByType
      else {
        val fresh = new ConcurrentHashMap[MethodType, MethodHandle]
        val existing = constructorHandles.putIfAbsent(clazz, fresh)
        if (existing eq null) fresh else existing
      }
    val cached = byType.get(methodType)
    if (cached ne null) cached
    else {
      val rawHandle =
        try lookup.findConstructor(clazz, methodType)
        catch {
          case _: IllegalAccessException =>
            try publicLookup.findConstructor(clazz, methodType)
            catch {
              case _: IllegalAccessException =>
                MethodHandles.privateLookupIn(clazz, lookup).findConstructor(clazz, methodType)
            }
        }
      val handle = rawHandle
        .asFixedArity()
        .asSpreader(classOf[Array[AnyRef]], methodType.parameterCount())
        .asType(genericConstructorType)
      val existing = byType.putIfAbsent(methodType, handle)
      if (existing eq null) handle else existing
    }
  }

  private def moduleHandleFor(clazz: Class[?]): VarHandle = {
    val cached = moduleHandles.get(clazz)
    if (cached ne null) cached
    else {
      val handle =
        try lookup.findStaticVarHandle(clazz, "MODULE$", clazz)
        catch {
          case _: IllegalAccessException =>
            try publicLookup.findStaticVarHandle(clazz, "MODULE$", clazz)
            catch {
              case _: IllegalAccessException =>
                MethodHandles.privateLookupIn(clazz, lookup).findStaticVarHandle(clazz, "MODULE$", clazz)
            }
        }
      val existing = moduleHandles.putIfAbsent(clazz, handle)
      if (existing eq null) handle else existing
    }
  }
}
