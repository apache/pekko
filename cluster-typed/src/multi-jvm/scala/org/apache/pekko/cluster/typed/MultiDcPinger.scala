/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.serialization.jackson.CborSerializable

object MultiDcPinger {

  sealed trait Command extends CborSerializable
  case class Ping(ref: ActorRef[Pong]) extends Command
  case object NoMore extends Command
  case class Pong(dc: String) extends CborSerializable

  def apply(): Behavior[Command] = Behaviors.setup[Command] { ctx =>
    val cluster = Cluster(ctx.system)
    Behaviors.receiveMessage[Command] {
      case Ping(ref) =>
        ref ! Pong(cluster.selfMember.dataCenter)
        Behaviors.same
      case NoMore =>
        Behaviors.stopped
    }
  }
}
