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

import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

import scala.annotation.tailrec
import scala.annotation.varargs

import org.apache.pekko
import pekko.japi.function.Creator

/**
 * Java API: Factory for Props instances.
 */
private[pekko] trait AbstractProps {

  /**
   * INTERNAL API
   */
  private[pekko] def validate(clazz: Class[_]): Unit = {
    if (Modifier.isAbstract(clazz.getModifiers)) {
      throw new IllegalArgumentException(s"Actor class [${clazz.getName}] must not be abstract")
    } else if (!classOf[Actor].isAssignableFrom(clazz) &&
      !classOf[IndirectActorProducer].isAssignableFrom(clazz)) {
      throw new IllegalArgumentException(
        s"Actor class [${clazz.getName}] must be subClass of org.apache.pekko.actor.Actor or org.apache.pekko.actor.IndirectActorProducer.")
    }
  }

  /**
   * Java API: create a Props given a class and its constructor arguments.
   */
  @varargs
  def create(clazz: Class[_], args: AnyRef*): Props =
    new Props(deploy = Props.defaultDeploy, clazz = clazz, args = args.toList)

  /**
   * Create new Props from the given [[pekko.japi.Creator]] with the type set to the given actorClass.
   */
  def create[T <: Actor](actorClass: Class[T], creator: Creator[T]): Props = {
    checkCreatorClosingOver(creator.getClass)
    create(classOf[CreatorConsumer], actorClass, creator)
  }

  private def checkCreatorClosingOver(clazz: Class[_]): Unit = {
    val enclosingClass = clazz.getEnclosingClass

    def hasDeclaredConstructorWithEmptyParams(declaredConstructors: Array[Constructor[_]]): Boolean = {
      @tailrec def loop(i: Int): Boolean = {
        if (i == declaredConstructors.length) false
        else {
          if (declaredConstructors(i).getParameterCount == 0)
            true
          else
            loop(i + 1) // recur
        }
      }
      loop(0)
    }

    def hasDeclaredConstructorWithEnclosingClassParam(declaredConstructors: Array[Constructor[_]]): Boolean = {
      @tailrec def loop(i: Int): Boolean = {
        if (i == declaredConstructors.length) false
        else {
          val c = declaredConstructors(i)
          if (c.getParameterCount >= 1 && c.getParameterTypes()(0) == enclosingClass)
            true
          else
            loop(i + 1) // recur
        }
      }
      loop(0)
    }

    def hasValidConstructor: Boolean = {
      val constructorsLength = clazz.getConstructors.length
      if (constructorsLength > 0)
        true
      else {
        val decl = clazz.getDeclaredConstructors
        // the hasDeclaredConstructorWithEnclosingClassParam check is for supporting `new Creator<SomeActor> {`
        // which was supported in versions before 2.4.5
        hasDeclaredConstructorWithEmptyParams(decl) || !hasDeclaredConstructorWithEnclosingClassParam(decl)
      }
    }

    if ((enclosingClass ne null) && !hasValidConstructor)
      throw new IllegalArgumentException(
        "cannot use non-static local Creator to create actors; make it static (e.g. local to a static method) or top-level")
  }
}
