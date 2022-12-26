/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed

import scala.concurrent.ExecutionContextExecutor

import org.apache.pekko.annotation.InternalApi

object Dispatchers {

  /**
   * The id of the default dispatcher, also the full key of the
   * configuration of the default dispatcher.
   */
  final val DefaultDispatcherId = "pekko.actor.default-dispatcher"

  /**
   * INTERNAL API
   */
  @InternalApi final val InternalDispatcherId = "pekko.actor.internal-dispatcher"
}

/**
 * An [[ActorSystem]] looks up all its thread pools via a Dispatchers instance.
 */
abstract class Dispatchers {
  def lookup(selector: DispatcherSelector): ExecutionContextExecutor
  def shutdown(): Unit
}
