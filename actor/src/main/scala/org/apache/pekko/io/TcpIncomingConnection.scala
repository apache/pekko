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

import java.nio.channels.SocketChannel

import scala.collection.immutable

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.io.Inet.SocketOption

/**
 * An actor handling the connection state machine for an incoming, already connected
 * SocketChannel.
 *
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[io] class TcpIncomingConnection(
    _tcp: TcpExt,
    _channel: SocketChannel,
    registry: ChannelRegistry,
    bindHandler: ActorRef,
    options: immutable.Traversable[SocketOption],
    readThrottling: Boolean)
    extends TcpConnection(_tcp, _channel, readThrottling) {

  signDeathPact(bindHandler)

  registry.register(channel, initialOps = 0)

  def receive = {
    case registration: ChannelRegistration => completeConnect(registration, bindHandler, options)
  }
}
