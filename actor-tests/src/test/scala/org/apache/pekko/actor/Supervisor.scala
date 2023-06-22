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

/**
 * For testing Supervisor behavior, normally you don't supply the strategy
 * from the outside like this.
 */
class Supervisor(override val supervisorStrategy: SupervisorStrategy) extends Actor {

  def receive = {
    case x: Props => sender() ! context.actorOf(x)
  }
  // need to override the default of stopping all children upon restart, tests rely on keeping them around
  override def preRestart(cause: Throwable, msg: Option[Any]): Unit = {}
}
