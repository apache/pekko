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

package docs.stream.operators.flow

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.BroadcastHub
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Framing
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.MergeHub
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.Tcp
import org.apache.pekko.stream.testkit.TestPublisher
import org.apache.pekko.stream.testkit.TestSubscriber
import org.apache.pekko.util.ByteString

import scala.concurrent.duration._

object FromSinkAndSource {

  implicit val system: ActorSystem = ???

  def halfClosedTcpServer(): Unit = {
    // #halfClosedTcpServer
    // close in immediately
    val sink = Sink.cancelled[ByteString]
    // periodic tick out
    val source =
      Source.tick(1.second, 1.second, "tick").map(_ => ByteString(System.currentTimeMillis().toString + "\n"))

    val serverFlow = Flow.fromSinkAndSource(sink, source)

    Tcp(system).bind("127.0.0.1", 9999, halfClose = true).runForeach { incomingConnection =>
      incomingConnection.handleWith(serverFlow)
    }
    // #halfClosedTcpServer
  }

  def chat(): Unit = {
    // #chat
    val (sink, source) = MergeHub.source[String].toMat(BroadcastHub.sink[String])(Keep.both).run()

    val framing = Framing.delimiter(ByteString("\n"), 1024)

    val sinkWithFraming = framing.map(bytes => bytes.utf8String).to(sink)
    val sourceWithFraming = source.map(text => ByteString(text + "\n"))

    val serverFlow = Flow.fromSinkAndSource(sinkWithFraming, sourceWithFraming)

    Tcp(system).bind("127.0.0.1", 9999).runForeach { incomingConnection =>
      incomingConnection.handleWith(serverFlow)
    }
    // #chat
  }

  def testing(): Unit = {
    def myApiThatTakesAFlow[In, Out](flow: Flow[In, Out, NotUsed]): Unit = ???
    // #testing
    val inProbe = TestSubscriber.probe[String]()
    val outProbe = TestPublisher.probe[String]()
    val testFlow = Flow.fromSinkAndSource(Sink.fromSubscriber(inProbe), Source.fromPublisher(outProbe))

    myApiThatTakesAFlow(testFlow)
    inProbe.expectNext("first")
    outProbe.expectRequest()
    outProbe.sendError(new RuntimeException("test error"))
    // ...
    // #testing
  }
}
