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

package org.apache.pekko.osgi

import com.typesafe.config.{ Config, ConfigFactory }
import org.osgi.framework.BundleContext

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.util.unused

/**
 * Factory class to create ActorSystem implementations in an OSGi environment.  This mainly involves dealing with
 * bundle classloaders appropriately to ensure that configuration files and classes get loaded properly
 */
class OsgiActorSystemFactory(
    val context: BundleContext,
    val fallbackClassLoader: Option[ClassLoader],
    config: Config = ConfigFactory.empty) {

  /*
   * Classloader that delegates to the bundle for which the factory is creating an ActorSystem
   */
  private val classloader = BundleDelegatingClassLoader(context, fallbackClassLoader)

  /**
   * Creates the [[pekko.actor.ActorSystem]], using the name specified
   */
  def createActorSystem(name: String): ActorSystem = createActorSystem(Option(name))

  /**
   * Creates the [[pekko.actor.ActorSystem]], using the name specified.
   *
   * A default name (`bundle-&lt;bundle id&gt;-ActorSystem`) is assigned when you pass along [[scala.None]] instead.
   */
  def createActorSystem(name: Option[String]): ActorSystem =
    ActorSystem(actorSystemName(name), actorSystemConfig(context), classloader)

  /**
   * Strategy method to create the Config for the ActorSystem
   * ensuring that the default/reference configuration is loaded from the pekko-actor bundle.
   * Configuration files found in pekko-actor bundle
   */
  def actorSystemConfig(@unused context: BundleContext): Config = {
    config.withFallback(
      ConfigFactory
        .load(classloader)
        .withFallback(ConfigFactory.defaultReference(OsgiActorSystemFactory.pekkoActorClassLoader)))
  }

  /**
   * Determine the name for the [[pekko.actor.ActorSystem]]
   * Returns a default value of `bundle-&lt;bundle id&gt;-ActorSystem` is no name is being specified
   */
  def actorSystemName(name: Option[String]): String =
    name.getOrElse("bundle-%s-ActorSystem".format(context.getBundle.getBundleId))

}

object OsgiActorSystemFactory {

  /**
   * Class loader of pekko-actor bundle.
   */
  def pekkoActorClassLoader = classOf[ActorSystemActivator].getClassLoader

  /*
   * Create an [[OsgiActorSystemFactory]] instance to set up Pekko in an OSGi environment
   */
  def apply(context: BundleContext, config: Config): OsgiActorSystemFactory =
    new OsgiActorSystemFactory(context, Some(pekkoActorClassLoader), config)
}
