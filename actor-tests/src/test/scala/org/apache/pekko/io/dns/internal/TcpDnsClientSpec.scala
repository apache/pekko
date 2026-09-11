/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns.internal

import java.net.InetSocketAddress

import scala.collection.immutable.Seq

import org.apache.pekko
import pekko.actor.{ ActorKilledException, Kill, Props }
import pekko.io.Tcp
import pekko.io.Tcp.{ Connected, PeerClosed, Register }
import pekko.io.dns.{ RecordClass, RecordType }
import pekko.io.dns.internal.DnsClient.Answer
import pekko.testkit.{ EventFilter, ImplicitSender, PekkoSpec, TestProbe }

class TcpDnsClientSpec extends PekkoSpec with ImplicitSender {
  import TcpDnsClient._

  "The TCP DNS length prefix" should {
    // RFC 1035 section 4.2.2: each message is prefixed by its length as two big endian bytes.

    "round trip every representable length" in {
      for (length <- 0 to 65535) withClue(s"length $length: ") {
        val encoded = encodeLength(length)
        encoded.length should ===(2)
        decodeLength(encoded) should ===(length)
      }
    }

    "encode the length as two big endian bytes" in {
      encodeLength(0) should ===(pekko.util.ByteString(0, 0))
      encodeLength(1) should ===(pekko.util.ByteString(0, 1))
      encodeLength(255) should ===(pekko.util.ByteString(0, 255.toByte))
      encodeLength(256) should ===(pekko.util.ByteString(1, 0))
      encodeLength(65535) should ===(pekko.util.ByteString(255.toByte, 255.toByte))
    }

    "decode lengths whose bytes have the high bit set" in {
      // the bytes are unsigned on the wire; a naive signed conversion gets these wrong
      decodeLength(pekko.util.ByteString(0, 255.toByte)) should ===(255)
      decodeLength(pekko.util.ByteString(255.toByte, 0)) should ===(65280)
      decodeLength(pekko.util.ByteString(255.toByte, 255.toByte)) should ===(65535)
      decodeLength(pekko.util.ByteString(128.toByte, 128.toByte)) should ===(32896)
    }

    "ignore any bytes after the two byte prefix" in {
      decodeLength(pekko.util.ByteString(1, 2, 3, 4, 5)) should ===(258)
    }
  }

  "The async TCP DNS client" should {
    val exampleRequestMessage =
      Message(42, MessageFlags(), questions = Seq(Question("pekko.io", RecordType.A, RecordClass.IN)))
    val exampleResponseMessage = Message(42, MessageFlags(answer = true))
    val dnsServerAddress = InetSocketAddress.createUnresolved("foo", 53)
    val localAddress = InetSocketAddress.createUnresolved("localhost", 13441)

    "reconnect when the server closes the connection" in {
      val tcpExtensionProbe = TestProbe()
      val answerProbe = TestProbe()

      val client = system.actorOf(Props(new TcpDnsClient(tcpExtensionProbe.ref, dnsServerAddress, answerProbe.ref)))

      client ! exampleRequestMessage

      tcpExtensionProbe.expectMsg(Tcp.Connect(dnsServerAddress))
      tcpExtensionProbe.lastSender ! Connected(dnsServerAddress, localAddress)
      expectMsgType[Register]
      val registered = tcpExtensionProbe.lastSender

      expectMsgType[Tcp.Write]
      registered ! Tcp.Received(encodeLength(exampleResponseMessage.write().length) ++ exampleResponseMessage.write())

      answerProbe.expectMsg(Answer(42, Nil))

      // When a new request arrived after the connection is closed
      registered ! PeerClosed
      client ! exampleRequestMessage

      // Expect a reconnect
      tcpExtensionProbe.expectMsg(Tcp.Connect(dnsServerAddress))
    }

    "accept a fragmented TCP response" in {
      val tcpExtensionProbe = TestProbe()
      val answerProbe = TestProbe()

      val client = system.actorOf(Props(new TcpDnsClient(tcpExtensionProbe.ref, dnsServerAddress, answerProbe.ref)))

      client ! exampleRequestMessage

      tcpExtensionProbe.expectMsg(Tcp.Connect(dnsServerAddress))
      tcpExtensionProbe.lastSender ! Connected(dnsServerAddress, localAddress)
      expectMsgType[Register]
      val registered = tcpExtensionProbe.lastSender

      expectMsgType[Tcp.Write]
      val fullResponse = encodeLength(exampleResponseMessage.write().length) ++ exampleResponseMessage.write()
      registered ! Tcp.Received(fullResponse.take(8))
      registered ! Tcp.Received(fullResponse.drop(8))

      answerProbe.expectMsg(Answer(42, Nil))
    }

    "accept merged TCP responses" in {
      val tcpExtensionProbe = TestProbe()
      val answerProbe = TestProbe()

      val client = system.actorOf(Props(new TcpDnsClient(tcpExtensionProbe.ref, dnsServerAddress, answerProbe.ref)))

      client ! exampleRequestMessage
      client ! exampleRequestMessage.copy(id = 43)

      tcpExtensionProbe.expectMsg(Tcp.Connect(dnsServerAddress))
      tcpExtensionProbe.lastSender ! Connected(dnsServerAddress, localAddress)
      expectMsgType[Register]
      val registered = tcpExtensionProbe.lastSender

      expectMsgType[Tcp.Write]
      expectMsgType[Tcp.Write]
      val fullResponse =
        encodeLength(exampleResponseMessage.write().length) ++ exampleResponseMessage.write() ++
        encodeLength(exampleResponseMessage.write().length) ++ exampleResponseMessage.copy(id = 43).write()
      registered ! Tcp.Received(fullResponse.take(8))
      registered ! Tcp.Received(fullResponse.drop(8))

      answerProbe.expectMsg(Answer(42, Nil))
      answerProbe.expectMsg(Answer(43, Nil))
    }

    "fail when the connection just terminates" in {
      val tcpExtensionProbe = TestProbe()
      val answerProbe = TestProbe()
      val connectionProbe = TestProbe()

      val client = system.actorOf(Props(new TcpDnsClient(tcpExtensionProbe.ref, dnsServerAddress, answerProbe.ref)))

      client ! exampleRequestMessage

      tcpExtensionProbe.expectMsg(Tcp.Connect(dnsServerAddress))
      connectionProbe.send(tcpExtensionProbe.lastSender, Connected(dnsServerAddress, localAddress))
      connectionProbe.expectMsgType[Register]

      EventFilter[ActorKilledException](occurrences = 1).intercept {
        // simulate connection stopping due to register timeout => client must fail
        connectionProbe.ref ! Kill
      }
    }
  }
}
