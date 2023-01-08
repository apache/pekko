/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.actor.Actor
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] abstract class ExposedPublisherReceive(activeReceive: Actor.Receive, unhandled: Any => Unit)
    extends Actor.Receive {
  private var stash = List.empty[Any]

  def isDefinedAt(o: Any): Boolean = true

  def apply(o: Any): Unit = o match {
    case ep: ExposedPublisher =>
      receiveExposedPublisher(ep)
      if (stash.nonEmpty) {
        // we don't use sender() so this is alright
        stash.reverse.foreach { msg =>
          activeReceive.applyOrElse(msg, unhandled)
        }
      }
    case other =>
      stash ::= other
  }

  def receiveExposedPublisher(ep: ExposedPublisher): Unit
}
