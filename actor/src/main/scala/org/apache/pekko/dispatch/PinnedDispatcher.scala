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

package org.apache.pekko.dispatch

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.ActorCell

/**
 * Dedicates a unique thread for each actor passed in as reference. Served through its messageQueue.
 *
 * The preferred way of creating dispatchers is to define configuration of it and use the
 * the `lookup` method in [[pekko.dispatch.Dispatchers]].
 */
class PinnedDispatcher(
    _configurator: MessageDispatcherConfigurator,
    _actor: ActorCell,
    _id: String,
    _shutdownTimeout: FiniteDuration,
    _threadPoolConfig: ThreadPoolConfig)
    extends Dispatcher(
      _configurator,
      _id,
      Int.MaxValue,
      Duration.Zero,
      _threadPoolConfig.copy(corePoolSize = 1, maxPoolSize = 1),
      _shutdownTimeout) {

  @volatile
  private var owner: ActorCell = _actor

  // Relies on an external lock provided by MessageDispatcher.attach
  protected[pekko] override def register(actorCell: ActorCell) = {
    val actor = owner
    if ((actor ne null) && actorCell != actor)
      throw new IllegalArgumentException("Cannot register to anyone but " + actor)
    owner = actorCell
    super.register(actorCell)
  }
  // Relies on an external lock provided by MessageDispatcher.detach
  protected[pekko] override def unregister(actor: ActorCell) = {
    super.unregister(actor)
    owner = null
  }
}
