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

package org.apache.pekko.io

import scala.collection.immutable
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor._
import pekko.dispatch.{ RequiresMessageQueue, UnboundedMessageQueueSemantics }
import pekko.io.Inet.{ DatagramChannelCreator, SocketOption }
import pekko.io.Udp._
import scala.annotation.nowarn

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[io] class UdpSender(
    val udp: UdpExt,
    channelRegistry: ChannelRegistry,
    commander: ActorRef,
    options: immutable.Traversable[SocketOption])
    extends Actor
    with ActorLogging
    with WithUdpSend
    with RequiresMessageQueue[UnboundedMessageQueueSemantics] {

  val channel = {
    val datagramChannel = options
      .collectFirst {
        case creator: DatagramChannelCreator => creator
      }
      .getOrElse(DatagramChannelCreator())
      .create()
    datagramChannel.configureBlocking(false)
    val socket = datagramChannel.socket
    options.foreach(_.beforeDatagramBind(socket))

    datagramChannel
  }
  channelRegistry.register(channel, initialOps = 0)

  def receive: Receive = {
    case registration: ChannelRegistration =>
      options.foreach {
        case v2: Inet.SocketOptionV2 => v2.afterConnect(channel.socket)
        case _                       =>
      }
      commander ! SimpleSenderReady
      context.become(sendHandlers(registration))
  }

  override def postStop(): Unit = if (channel.isOpen) {
    log.debug("Closing DatagramChannel after being stopped")
    try channel.close()
    catch {
      case NonFatal(e) => log.debug("Error closing DatagramChannel: {}", e)
    }
  }
}
