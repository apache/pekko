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
