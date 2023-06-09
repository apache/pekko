/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns.internal

import java.net.{ InetAddress, InetSocketAddress }
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable.Seq
import scala.concurrent.duration.DurationInt

import org.apache.pekko
import pekko.actor.{ ActorRef, Props }
import pekko.io.Udp
import pekko.io.dns.{ ARecord, CachePolicy, RecordClass, RecordType }
import pekko.io.dns.internal.DnsClient.{ Answer, Question4 }
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestProbe }

class DnsClientSpec extends PekkoSpec with ImplicitSender {
  "The async DNS client" should {
    val exampleRequest = Question4(42, "pekko.io")
    val exampleRequestMessage =
      Message(42, MessageFlags(), questions = Seq(Question("pekko.io", RecordType.A, RecordClass.IN)))
    val exampleResponseMessage = Message(42, MessageFlags(answer = true))
    val exampleResponse = Answer(42, Nil)
    val dnsServerAddress = InetSocketAddress.createUnresolved("foo", 53)

    "not connect to the DNS server over TCP eagerly" in {
      val udpExtensionProbe = TestProbe()
      val tcpClientCreated = new AtomicBoolean(false)

      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient(): ActorRef = {
          tcpClientCreated.set(true)
          TestProbe().ref
        }
      }))

      client ! exampleRequest

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpExtensionProbe.lastSender ! Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325))

      expectMsgType[Udp.Send]

      client ! Udp.Received(
        exampleResponseMessage
          .copy(questions = exampleRequestMessage.questions)
          .write(), dnsServerAddress)

      expectMsg(exampleResponse)

      tcpClientCreated.get() should be(false)
    }

    "Defer dns query when id collision" in {
      val udpExtensionProbe = TestProbe()
      val tcpClientProbe = TestProbe()

      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient(): ActorRef = tcpClientProbe.ref
      }))

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpExtensionProbe.lastSender ! Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325))

      client ! exampleRequest
      client ! exampleRequest.copy(name = "apache.org") // will be stashed

      expectMsgType[Udp.Send]
      expectNoMessage(3.seconds)

      val pekkoRecord = ARecord("pekko.io", CachePolicy.Ttl.effectivelyForever, InetAddress.getByName("127.0.0.1"))
      val apacheRecord = ARecord("apache.org", CachePolicy.Ttl.effectivelyForever, InetAddress.getByName("127.0.0.1"))

      val pekkoResponse =
        Message(42, MessageFlags(answer = true), exampleRequestMessage.questions, Seq(pekkoRecord))

      client ! Udp.Received(
        pekkoResponse.write(),
        dnsServerAddress)

      val responsePekko = expectMsgType[Answer]
      responsePekko.id shouldBe 42

      expectMsgType[Udp.Send] // unstashed

      val apacheResponse =
        Message(
          42,
          MessageFlags(answer = true),
          Seq(Question("apache.org", RecordType.A, RecordClass.IN)), Seq(apacheRecord))

      client ! Udp.Received(
        apacheResponse.write(),
        dnsServerAddress)

      val responseApache = expectMsgType[Answer]
      responseApache.id shouldBe 42
    }

    "Fall back to TCP when the UDP response is truncated" in {
      val udpExtensionProbe = TestProbe()
      val tcpClientProbe = TestProbe()

      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient(): ActorRef = tcpClientProbe.ref
      }))

      client ! exampleRequest

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpExtensionProbe.lastSender ! Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325))

      expectMsgType[Udp.Send]
      client ! Udp.Received(Message(
          exampleRequest.id,
          MessageFlags(truncated = true),
          exampleRequestMessage.questions).write(), dnsServerAddress)

      tcpClientProbe.expectMsg(exampleRequestMessage)
      tcpClientProbe.reply(exampleResponse)

      expectMsg(exampleResponse)
    }
  }
}
