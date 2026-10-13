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

import java.nio.channels.DatagramChannel

import scala.annotation.nowarn
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor._
import pekko.dispatch.{ RequiresMessageQueue, UnboundedMessageQueueSemantics }
import pekko.io.Inet.DatagramChannelCreator
import pekko.io.Udp._

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[io] class UdpSender(
    val udp: UdpExt,
    channelRegistry: ChannelRegistry,
    commander: ActorRef,
    simpleSender: SimpleSender)
    extends Actor
    with ActorLogging
    with WithUdpSend
    with RequiresMessageQueue[UnboundedMessageQueueSemantics] {

  private val options = simpleSender.options

  val channel = {
    var datagramChannel: DatagramChannel = null
    try {
      datagramChannel = options
        .collectFirst {
          case creator: DatagramChannelCreator => creator
        }
        .getOrElse(DatagramChannelCreator())
        .create()
      datagramChannel.configureBlocking(false)
      val socket = datagramChannel.socket
      options.foreach { _.beforeDatagramBind(socket) }
      channelRegistry.register(datagramChannel, initialOps = 0)

      datagramChannel
    } catch {
      case NonFatal(e) =>
        if ((datagramChannel ne null) && datagramChannel.isOpen) {
          try datagramChannel.close()
          catch {
            case NonFatal(closeError) => log.debug("Error closing DatagramChannel: {}", closeError)
          }
        }
        commander ! CommandFailed(simpleSender)
        log.debug("Failed to create UDP simple sender: {}", e)
        context.stop(self)
        null
    }
  }

  def receive: Receive = {
    case registration: ChannelRegistration if channel ne null =>
      options.foreach {
        case v2: Inet.SocketOptionV2 => v2.afterConnect(channel.socket)
        case _                       =>
      }
      commander ! SimpleSenderReady
      context.become(sendHandlers(registration))
  }

  override def postStop(): Unit = if ((channel ne null) && channel.isOpen) {
    log.debug("Closing DatagramChannel after being stopped")
    try channel.close()
    catch {
      case NonFatal(e) => log.debug("Error closing DatagramChannel: {}", e)
    }
  }
}
