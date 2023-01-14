/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.osgi.test

import PingPong._
import org.osgi.framework.BundleContext

import org.apache.pekko
import pekko.actor.{ ActorSystem, Props }
import pekko.osgi.ActorSystemActivator

/**
 * A set of [[pekko.osgi.ActorSystemActivator]]s for testing purposes
 */
object TestActivators {

  val ACTOR_SYSTEM_NAME_PATTERN = "actor-system-for-bundle-%s"

}

/**
 * Simple ActorSystemActivator that starts the sample ping-pong application
 */
class PingPongActorSystemActivator extends ActorSystemActivator {

  def configure(context: BundleContext, system: ActorSystem): Unit = {
    system.actorOf(Props[PongActor](), name = "pong")
    registerService(context, system)
  }

}

/**
 * [[pekko.osgi.ActorSystemActivator]] implementation that determines [[pekko.actor.ActorSystem]] name at runtime
 */
class RuntimeNameActorSystemActivator extends ActorSystemActivator {

  def configure(context: BundleContext, system: ActorSystem) = registerService(context, system)

  override def getActorSystemName(context: BundleContext) =
    TestActivators.ACTOR_SYSTEM_NAME_PATTERN.format(context.getBundle.getBundleId)

}
