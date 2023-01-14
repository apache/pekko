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

package typed.tutorial_3

/*
//#read-protocol-1
package com.example

//#read-protocol-1
 */

import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.Signal

object DeviceInProgress1 {

  // #read-protocol-1
  import org.apache.pekko.actor.typed.ActorRef

  object Device {
    sealed trait Command
    final case class ReadTemperature(replyTo: ActorRef[RespondTemperature]) extends Command
    final case class RespondTemperature(value: Option[Double])
  }
  // #read-protocol-1

}

object DeviceInProgress2 {
  import org.apache.pekko.actor.typed.ActorRef

  // #device-with-read
  import org.apache.pekko
  import pekko.actor.typed.Behavior
  import pekko.actor.typed.scaladsl.AbstractBehavior
  import pekko.actor.typed.scaladsl.ActorContext
  import pekko.actor.typed.scaladsl.Behaviors
  import pekko.actor.typed.scaladsl.LoggerOps

  object Device {
    def apply(groupId: String, deviceId: String): Behavior[Command] =
      Behaviors.setup(context => new Device(context, groupId, deviceId))

    // #read-protocol-2
    sealed trait Command
    final case class ReadTemperature(requestId: Long, replyTo: ActorRef[RespondTemperature]) extends Command
    final case class RespondTemperature(requestId: Long, value: Option[Double])
    // #read-protocol-2
  }

  class Device(context: ActorContext[Device.Command], groupId: String, deviceId: String)
      extends AbstractBehavior[Device.Command](context) {
    import Device._

    var lastTemperatureReading: Option[Double] = None

    context.log.info2("Device actor {}-{} started", groupId, deviceId)

    override def onMessage(msg: Command): Behavior[Command] = {
      msg match {
        case ReadTemperature(id, replyTo) =>
          replyTo ! RespondTemperature(id, lastTemperatureReading)
          this
      }
    }

    override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
      case PostStop =>
        context.log.info2("Device actor {}-{} stopped", groupId, deviceId)
        this
    }

  }
  // #device-with-read

}

object DeviceInProgress3 {

  object Device {
    // #write-protocol-1
    sealed trait Command
    final case class RecordTemperature(value: Double) extends Command
    // #write-protocol-1
  }
}
