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

package docs.io

//#imports
import java.net.InetSocketAddress

import org.apache.pekko
import pekko.actor.{ Actor, ActorRef, Props }
import pekko.io.{ IO, Tcp }
import pekko.util.ByteString
//#imports
import scala.concurrent.duration._

import pekko.testkit.PekkoSpec

class DemoActor extends Actor {
  // #manager
  import org.apache.pekko.io.{ IO, Tcp }
  import context.system // implicitly used by IO(Tcp)

  val manager = IO(Tcp)
  // #manager

  def receive = Actor.emptyBehavior
}

//#server
class Server extends Actor {

  import Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 0))

  def receive = {
    case b @ Bound(localAddress) =>
      // #do-some-logging-or-setup
      context.parent ! b
    // #do-some-logging-or-setup

    case CommandFailed(_: Bind) => context.stop(self)

    case c @ Connected(remote, local) =>
      // #server
      context.parent ! c
      // #server
      val handler = context.actorOf(Props[SimplisticHandler]())
      val connection = sender()
      connection ! Register(handler)
  }

}
//#server

//#simplistic-handler
class SimplisticHandler extends Actor {
  import Tcp._
  def receive = {
    case Received(data) => sender() ! Write(data)
    case PeerClosed     => context.stop(self)
  }
}
//#simplistic-handler

//#client
object Client {
  def props(remote: InetSocketAddress, replies: ActorRef) =
    Props(classOf[Client], remote, replies)
}

class Client(remote: InetSocketAddress, listener: ActorRef) extends Actor {

  import Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)

  def receive = {
    case CommandFailed(_: Connect) =>
      listener ! "connect failed"
      context.stop(self)

    case c @ Connected(remote, local) =>
      listener ! c
      val connection = sender()
      connection ! Register(self)
      context.become {
        case data: ByteString =>
          connection ! Write(data)
        case CommandFailed(w: Write) =>
          // O/S buffer was full
          listener ! "write failed"
        case Received(data) =>
          listener ! data
        case "close" =>
          connection ! Close
        case _: ConnectionClosed =>
          listener ! "connection closed"
          context.stop(self)
      }
  }
}
//#client

class IODocSpec extends PekkoSpec {

  class Parent extends Actor {
    context.actorOf(Props[Server](), "server")
    def receive = {
      case msg => testActor.forward(msg)
    }
  }

  "demonstrate connect" in {
    val server = system.actorOf(Props(classOf[Parent], this), "parent")
    val listen = expectMsgType[Tcp.Bound].localAddress
    val client = system.actorOf(Client.props(listen, testActor), "client1")

    watch(client)

    val c1, c2 = expectMsgType[Tcp.Connected]
    c1.localAddress should be(c2.remoteAddress)
    c2.localAddress should be(c1.remoteAddress)

    client ! ByteString("hello")
    expectMsgType[ByteString].utf8String should be("hello")

    client ! "close"
    expectMsg("connection closed")
    expectTerminated(client, 1.second)
  }

}
