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

package org.apache.pekko.remote.testconductor

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

import org.apache.pekko
import pekko.actor.{ AddressFromURIString, PoisonPill, Props, Status }
import pekko.remote.testconductor.Controller.NodeInfo
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec

object ControllerSpec {
  val config = """
    pekko.testconductor.barrier-timeout = 5s
    pekko.actor.provider = remote
    pekko.actor.debug.fsm = on
    pekko.actor.debug.lifecycle = on
    """
}

class ControllerSpec extends PekkoSpec(ControllerSpec.config) with ImplicitSender {

  val A = RoleName("a")
  val B = RoleName("b")

  "A Controller" must {

    "publish its nodes" in {
      val c = system.actorOf(Props(classOf[Controller], 1, new InetSocketAddress(InetAddress.getLocalHost, 0)))
      c ! NodeInfo(A, AddressFromURIString("pekko://sys"), testActor)
      expectMsg(ToClient(Done))
      c ! NodeInfo(B, AddressFromURIString("pekko://sys"), testActor)
      expectMsg(ToClient(Done))
      c ! Controller.GetNodes
      expectMsgType[Iterable[RoleName]].toSet should ===(Set(A, B))
      c ! PoisonPill // clean up so network connections don't accumulate during test run
    }

    "reply with the actually bound socket address" in {
      val c = system.actorOf(Props(classOf[Controller], 1, new InetSocketAddress(InetAddress.getLocalHost, 0)))
      c ! Controller.GetSockAddr
      val addr = expectMsgType[InetSocketAddress]
      addr.getPort should not be 0
      c ! PoisonPill // clean up so network connections don't accumulate during test run
    }

    "reply with failure when the conductor port cannot be bound" in {
      // bind to the same specific address the controller will use, a wildcard
      // bind would not conflict on all platforms
      val serverSocket = new ServerSocket(0, 50, InetAddress.getLocalHost)
      try {
        val port = serverSocket.getLocalPort
        val c =
          system.actorOf(Props(classOf[Controller], 1, new InetSocketAddress(InetAddress.getLocalHost, port)))
        c ! Controller.GetSockAddr
        // the bind failure must be reported to the requester instead of being
        // thrown out of the message handler by a blocking sync()
        expectMsgType[Status.Failure]
      } finally serverSocket.close()
    }

  }

}
