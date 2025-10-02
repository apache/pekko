/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.setup

import java.util.Optional

import scala.annotation.varargs
import scala.jdk.OptionConverters._
import scala.reflect.ClassTag

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * Marker supertype for a setup part that can be put inside [[pekko.actor.setup.ActorSystemSetup]], if a specific concrete setup
 * is not specified in the actor system setup that means defaults are used (usually from the config file) - no concrete
 * setup instance should be mandatory in the [[pekko.actor.setup.ActorSystemSetup]] that an actor system is created with.
 */
abstract class Setup {

  /**
   * Construct an [[pekko.actor.setup.ActorSystemSetup]] with this setup combined with another one. Allows for
   * fluent creation of settings. If `other` is a setting of the same concrete [[pekko.actor.setup.Setup]] as this
   * it will replace this.
   */
  final def and(other: Setup): ActorSystemSetup = ActorSystemSetup(this, other)

}

object ActorSystemSetup {

  val empty = new ActorSystemSetup(Map.empty)

  /**
   * Scala API: Create an [[pekko.actor.setup.ActorSystemSetup]] containing all the provided settings
   */
  def apply(settings: Setup*): ActorSystemSetup =
    new ActorSystemSetup(settings.map(s => s.getClass -> s).toMap)

  /**
   * Java API: Create an [[pekko.actor.setup.ActorSystemSetup]] containing all the provided settings
   */
  @varargs
  def create(settings: Setup*): ActorSystemSetup = apply(settings: _*)
}

/**
 * A set of setup settings for programmatic configuration of the actor system.
 *
 * Constructor is *Internal API*. Use the factory methods [[ActorSystemSetup#create]] and [[ActorSystemSetup#apply]] to create
 * instances.
 */
final class ActorSystemSetup private[pekko] (@InternalApi private[pekko] val setups: Map[Class[_], AnyRef]) {

  /**
   * Java API: Extract a concrete [[pekko.actor.setup.Setup]] of type `T` if it is defined in the settings.
   */
  def get[T <: Setup](clazz: Class[T]): Optional[T] = {
    setups.get(clazz).toJava.asInstanceOf[Optional[T]]
  }

  /**
   * Scala API: Extract a concrete [[pekko.actor.setup.Setup]] of type `T` if it is defined in the settings.
   */
  def get[T <: Setup: ClassTag]: Option[T] = {
    val clazz = implicitly[ClassTag[T]].runtimeClass
    setups.get(clazz).asInstanceOf[Option[T]]
  }

  /**
   * Add a concrete [[pekko.actor.setup.Setup]]. If a setting of the same concrete [[pekko.actor.setup.Setup]] already is
   * present it will be replaced.
   */
  def withSetup[T <: Setup](t: T): ActorSystemSetup = {
    new ActorSystemSetup(setups + (t.getClass -> t))
  }

  /**
   * alias for `withSetup` allowing for fluent combination of settings: `a and b and c`, where `a`, `b` and `c` are
   * concrete [[pekko.actor.setup.Setup]] instances. If a setting of the same concrete [[pekko.actor.setup.Setup]] already is
   * present it will be replaced.
   */
  def and[T <: Setup](t: T): ActorSystemSetup = withSetup(t)

  override def toString: String = s"""ActorSystemSettings(${setups.keys.map(_.getName).mkString(",")})"""
}
