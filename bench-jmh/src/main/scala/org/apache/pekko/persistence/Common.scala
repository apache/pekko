/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import org.apache.pekko.actor.Actor

/** only as a "the best we could possibly get" baseline, does not persist anything */
class BaselineActor(respondAfter: Int) extends Actor {
  override def receive = {
    case n: Int => if (n == respondAfter) sender() ! n
  }
}

final case class Evt(i: Int)
