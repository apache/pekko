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

import java.net.StandardSocketOptions
import java.nio.channels.DatagramChannel
import java.nio.channels.NetworkChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

import org.apache.pekko
import pekko.testkit.PekkoSpec

class InetSocketOptionSpec extends PekkoSpec {

  "Inet.SO.ReusePort" must {

    "set SO_REUSEPORT on TCP server sockets before bind" in {
      val channel = ServerSocketChannel.open()
      try {
        pendingIfReusePortUnsupported(channel)

        Inet.SO.ReusePort(true).beforeServerSocketBind(channel.socket)

        channel.getOption(StandardSocketOptions.SO_REUSEPORT).booleanValue should ===(true)
      } finally {
        channel.close()
      }
    }

    "set SO_REUSEPORT on TCP client sockets before connect" in {
      val channel = SocketChannel.open()
      try {
        pendingIfReusePortUnsupported(channel)

        Tcp.SO.ReusePort(true).beforeConnect(channel.socket)

        channel.getOption(StandardSocketOptions.SO_REUSEPORT).booleanValue should ===(true)
      } finally {
        channel.close()
      }
    }

    "set SO_REUSEPORT on UDP sockets before bind" in {
      val channel = DatagramChannel.open()
      try {
        pendingIfReusePortUnsupported(channel)

        Udp.SO.ReusePort(true).beforeDatagramBind(channel.socket)

        channel.getOption(StandardSocketOptions.SO_REUSEPORT).booleanValue should ===(true)
      } finally {
        channel.close()
      }
    }

    "be available through the Java TCP and UDP socket option APIs" in {
      TcpSO.reusePort(true) should ===(Inet.SO.ReusePort(true))
      UdpSO.reusePort(false) should ===(Inet.SO.ReusePort(false))
    }
  }

  private def pendingIfReusePortUnsupported(channel: NetworkChannel): Unit =
    if (!channel.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
      pending
    }
}
