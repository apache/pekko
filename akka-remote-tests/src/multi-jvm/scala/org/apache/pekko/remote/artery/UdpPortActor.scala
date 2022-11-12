/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.Actor
import pekko.remote.RARP
import pekko.serialization.jackson.CborSerializable
import pekko.testkit.SocketUtil

object UdpPortActor {
  case object GetUdpPort extends CborSerializable
}

/**
 * Used for exchanging free udp port between multi-jvm nodes
 */
class UdpPortActor extends Actor {
  import UdpPortActor._

  val port =
    SocketUtil.temporaryServerAddress(RARP(context.system).provider.getDefaultAddress.host.get, udp = true).getPort

  def receive = {
    case GetUdpPort => sender() ! port
  }
}
