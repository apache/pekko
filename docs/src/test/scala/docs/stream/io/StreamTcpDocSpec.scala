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

package docs.stream.io

import java.util.concurrent.atomic.AtomicReference

import org.apache.pekko.stream.scaladsl.Tcp._
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.testkit.PekkoSpec
import org.apache.pekko.testkit.TestProbe
import org.apache.pekko.util.ByteString

import scala.concurrent.Future
import org.apache.pekko.testkit.SocketUtil
import scala.concurrent.ExecutionContext

class StreamTcpDocSpec extends PekkoSpec {

  implicit val ec: ExecutionContext = system.dispatcher

  // silence sysout
  def println(s: String) = ()

  "simple server connection" in {
    {
      // #echo-server-simple-bind
      val binding: Future[ServerBinding] =
        Tcp(system).bind("127.0.0.1", 8888).to(Sink.ignore).run()

      binding.map { b =>
        b.unbind().onComplete {
          case _ => // ...
        }
      }
      // #echo-server-simple-bind
    }
    {
      val (host, port) = SocketUtil.temporaryServerHostnameAndPort()
      // #echo-server-simple-handle
      import org.apache.pekko.stream.scaladsl.Framing

      val connections: Source[IncomingConnection, Future[ServerBinding]] =
        Tcp(system).bind(host, port)
      connections.runForeach { connection =>
        println(s"New connection from: ${connection.remoteAddress}")

        val echo = Flow[ByteString]
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
          .map(_.utf8String)
          .map(_ + "!!!\n")
          .map(ByteString(_))

        connection.handleWith(echo)
      }
      // #echo-server-simple-handle
    }
  }

  "initial server banner echo server" in {
    val localhost = SocketUtil.temporaryServerAddress()

    val connections = Tcp(system).bind(localhost.getHostString, localhost.getPort)
    val serverProbe = TestProbe()

    import org.apache.pekko.stream.scaladsl.Framing
    val binding =
      // #welcome-banner-chat-server
      connections
        .to(Sink.foreach { connection =>
          // server logic, parses incoming commands
          val commandParser = Flow[String].takeWhile(_ != "BYE").map(_ + "!")

          import connection._
          val welcomeMsg = s"Welcome to: $localAddress, you are: $remoteAddress!"
          val welcome = Source.single(welcomeMsg)

          val serverLogic = Flow[ByteString]
            .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
            .map(_.utf8String)
            // #welcome-banner-chat-server
            .map { command =>
              serverProbe.ref ! command; command
            }
            // #welcome-banner-chat-server
            .via(commandParser)
            // merge in the initial banner after parser
            .merge(welcome)
            .map(_ + "\n")
            .map(ByteString(_))

          connection.handleWith(serverLogic)
        })
        .run()
    // #welcome-banner-chat-server

    // make sure server is started before we connect
    binding.futureValue

    import org.apache.pekko.stream.scaladsl.Framing

    val input = new AtomicReference("Hello world" :: "What a lovely day" :: Nil)
    def readLine(prompt: String): String =
      input.get() match {
        case all @ cmd :: tail if input.compareAndSet(all, tail) => cmd
        case _                                                   => "q"
      }

    {
      // just for docs, never actually used
      // #repl-client
      val connection = Tcp(system).outgoingConnection("127.0.0.1", 8888)
      // #repl-client
    }

    {
      val connection = Tcp(system).outgoingConnection(localhost)
      // #repl-client

      val replParser =
        Flow[String].takeWhile(_ != "q").concat(Source.single("BYE")).map(elem => ByteString(s"$elem\n"))

      val repl = Flow[ByteString]
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
        .map(_.utf8String)
        .map(text => println("Server: " + text))
        .map(_ => readLine("> "))
        .via(replParser)

      val connected = connection.join(repl).run()
      // #repl-client

      // make sure we have a connection or fail already here
      connected.futureValue
    }

    serverProbe.expectMsg("Hello world")
    serverProbe.expectMsg("What a lovely day")
    serverProbe.expectMsg("BYE")
  }
}
