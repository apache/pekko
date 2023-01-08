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

package org.apache.pekko.io

import org.apache.pekko
import pekko.actor._

/**
 * Entry point to Akka’s IO layer.
 *
 * @see <a href="https://akka.io/docs/">the Akka online documentation</a>
 */
object IO {

  trait Extension extends pekko.actor.Extension {
    def manager: ActorRef
  }

  /**
   * Scala API: obtain a reference to the manager actor for the given IO extension,
   * for example [[Tcp]] or [[Udp]].
   *
   * For the Java API please refer to the individual extensions directly.
   */
  def apply[T <: Extension](key: ExtensionId[T])(implicit system: ActorSystem): ActorRef = key(system).manager

}
