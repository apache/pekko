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

import java.net.{ InetAddress, InetSocketAddress }
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.{ immutable => im }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Status.Failure
import pekko.actor.{ ActorRef, ExtendedActorSystem, Props }
import pekko.io.dns.internal.DnsClient.{
  DropRequest,
  Dropped,
  DuplicateId,
  Question4,
  Question6,
  TcpDnsAnswer,
  UdpDnsAnswer
}
import pekko.io.dns.{ ARecord, CachePolicy, DnsProtocol, DnsSettings, IdGenerator, RecordClass, RecordType }
import pekko.io.{ SimpleDnsCache, Tcp, Udp }
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestProbe }

import com.typesafe.config.ConfigFactory

class DnsClientSpec extends PekkoSpec with ImplicitSender {
  "The async DNS client" should {
    val exampleRequest = Question4(42, "pekko.io")
    val exampleQuestion = Question("pekko.io", RecordType.A, RecordClass.IN)
    val exampleRequestMessage = Message(42, MessageFlags(), questions = im.Seq(exampleQuestion))
    val exampleResponseMessage = Message(42, MessageFlags(answer = true), questions = im.Seq(exampleQuestion))
    val exampleResponse = UdpDnsAnswer(42, Nil)
    val dnsServerAddress = InetSocketAddress.createUnresolved("foo", 53)

    "not connect to the DNS server over TCP eagerly" in {
      val udpExtensionProbe = TestProbe()
      val tcpClientCreated = new AtomicBoolean(false)

      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient() = {
          tcpClientCreated.set(true)
          TestProbe().ref
        }
      }))

      client ! exampleRequest

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpExtensionProbe.lastSender ! Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325))

      expectMsgType[Udp.Send]
      client ! Udp.Received(exampleResponseMessage.write(), dnsServerAddress)

      expectMsg(exampleResponse)

      tcpClientCreated.get() should be(false)
    }

    for (includeQuestion <- Seq(false, true)) {
      s"fall back to TCP for a truncated response (echoed question = $includeQuestion)" in {
        val udpExtensionProbe = TestProbe()
        val tcpClientProbe = TestProbe()

        val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
          override val udp = udpExtensionProbe.ref

          override def createTcpClient() = tcpClientProbe.ref
        }))

        client ! exampleRequest

        udpExtensionProbe.expectMsgType[Udp.Bind]
        udpExtensionProbe.lastSender ! Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325))

        expectMsgType[Udp.Send]
        val questions = if (includeQuestion) im.Seq(exampleQuestion) else im.Seq.empty
        val truncated = Message(exampleRequest.id, MessageFlags(answer = true, truncated = true), questions = questions)
        client ! Udp.Received(truncated.write(), dnsServerAddress)

        tcpClientProbe.expectMsg(exampleRequestMessage)
        tcpClientProbe.reply(TcpDnsAnswer(42, Nil))

        expectMsg(TcpDnsAnswer(42, Nil))

        // Completion releases both the id and its transport state for the next lookup.
        client ! exampleRequest
        expectMsgType[Udp.Send]
        client ! Udp.Received(exampleResponseMessage.write(), dnsServerAddress)
        expectMsg(exampleResponse)
      }
    }

    "dispatch concurrent fallback requests independently of another UDP lookup" in {
      val udpExtensionProbe = TestProbe()
      val udpSocketProbe = TestProbe()
      val tcpClientProbe = TestProbe()
      val firstRequester = TestProbe()
      val secondRequester = TestProbe()
      val udpRequester = TestProbe()
      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient() = tcpClientProbe.ref
      }))

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpSocketProbe.send(
        udpExtensionProbe.lastSender,
        Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325)))

      firstRequester.send(client, exampleRequest)
      udpSocketProbe.expectMsgType[Udp.Send]
      secondRequester.send(client, exampleRequest.copy(id = 43))
      udpSocketProbe.expectMsgType[Udp.Send]

      val truncated = exampleResponseMessage.copy(flags = MessageFlags(answer = true, truncated = true))
      udpSocketProbe.send(client, Udp.Received(truncated.write(), dnsServerAddress))
      tcpClientProbe.expectMsg(exampleRequestMessage)
      udpSocketProbe.send(client, Udp.Received(truncated.copy(id = 43).write(), dnsServerAddress))
      tcpClientProbe.expectMsg(exampleRequestMessage.copy(id = 43))

      udpRequester.send(client, exampleRequest.copy(id = 44))
      udpSocketProbe.expectMsgType[Udp.Send]
      udpSocketProbe.send(client, Udp.Received(exampleResponseMessage.copy(id = 44).write(), dnsServerAddress))
      udpRequester.expectMsg(exampleResponse.copy(id = 44))

      // Independent lookups may finish in a different order from their requests.
      tcpClientProbe.send(client, TcpDnsAnswer(43, Nil))
      secondRequester.expectMsg(TcpDnsAnswer(43, Nil))
      tcpClientProbe.send(client, TcpDnsAnswer(42, Nil))
      firstRequester.expectMsg(TcpDnsAnswer(42, Nil))
    }

    "complete a fresh lookup after resolver timeout drops a closed TCP lookup" in {
      val udpExtensionProbe = TestProbe()
      val udpSocketProbe = TestProbe()
      val tcpExtensionProbe = TestProbe()
      val firstConnection = TestProbe()
      val nextConnection = TestProbe()
      val droppedProbe = TestProbe()
      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref
        override val tcp = tcpExtensionProbe.ref

        override def ready(socket: ActorRef): Receive = {
          val underlying = super.ready(socket)
          val observed: Receive = {
            case msg if underlying.isDefinedAt(msg) =>
              underlying(msg)
              msg match {
                case dropped: DropRequest => droppedProbe.ref ! dropped
                case _                    =>
              }
          }
          observed
        }
      }))
      val settings = new DnsSettings(system.asInstanceOf[ExtendedActorSystem],
        ConfigFactory.parseString("""
          nameservers = ["one"]
          resolve-timeout = 2s
          search-domains = []
          ndots = 1
          positive-ttl = never
          negative-ttl = never
        """))
      val ids = new IdGenerator {
        private var next: Short = 42
        override def nextId(): Short = {
          val id = next
          next = (next + 1).toShort
          id
        }
      }
      val resolver =
        system.actorOf(Props(new AsyncDnsResolver(settings, new SimpleDnsCache(), (_, _) => List(client), ids)))
      val request = DnsProtocol.Resolve(exampleRequest.name, DnsProtocol.Ip(ipv4 = true, ipv6 = false))
      val localAddress = InetSocketAddress.createUnresolved("localhost", 41325)

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpSocketProbe.send(udpExtensionProbe.lastSender, Udp.Bound(localAddress))
      val truncated = exampleResponseMessage.copy(flags = MessageFlags(answer = true, truncated = true))

      resolver ! request
      udpSocketProbe.expectMsgType[Udp.Send]
      udpSocketProbe.send(client, Udp.Received(truncated.write(), dnsServerAddress))
      tcpExtensionProbe.expectMsg(Tcp.Connect(dnsServerAddress))
      val tcpClient = tcpExtensionProbe.lastSender
      firstConnection.send(tcpClient, Tcp.Connected(dnsServerAddress, localAddress))
      firstConnection.expectMsgType[Tcp.Register]
      firstConnection.expectMsgType[Tcp.Write]

      // An orderly close leaves the pending lookup for the resolver's timeout/drop path.
      firstConnection.send(tcpClient, Tcp.PeerClosed)
      expectMsgPF(5.seconds) {
        case Failure(_: AsyncDnsResolver.ResolveFailedException) =>
      }
      droppedProbe.expectMsg(DropRequest(exampleRequest))

      resolver ! request
      val freshSend = udpSocketProbe.expectMsgType[Udp.Send]
      val freshId = Message.parse(freshSend.payload).id
      freshId shouldBe 43.toShort
      udpSocketProbe.send(client, Udp.Received(truncated.copy(id = freshId).write(), dnsServerAddress))
      tcpExtensionProbe.expectMsg(Tcp.Connect(dnsServerAddress))
      tcpExtensionProbe.lastSender shouldBe tcpClient
      nextConnection.send(tcpClient, Tcp.Connected(dnsServerAddress, localAddress))
      nextConnection.expectMsgType[Tcp.Register]
      nextConnection.expectMsgType[Tcp.Write]
      val response = exampleResponseMessage.copy(id = freshId).write()
      nextConnection.send(tcpClient, Tcp.Received(TcpDnsClient.encodeLength(response.length) ++ response))
      expectMsg(DnsProtocol.Resolved(exampleRequest.name, Nil))

      // The result also clears transport state when the real TCP child supplies it.
      client ! exampleRequest.copy(id = freshId)
      udpSocketProbe.expectMsgType[Udp.Send]
      udpSocketProbe.send(client, Udp.Received(response, dnsServerAddress))
      expectMsg(exampleResponse.copy(id = freshId))
    }

    for (useTcp <- Seq(false, true)) {
      s"release ${if (useTcp) "TCP" else "UDP"} request state when a lookup is dropped" in {
        val udpExtensionProbe = TestProbe()
        val tcpClientProbe = TestProbe()
        val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
          override val udp = udpExtensionProbe.ref

          override def createTcpClient() = tcpClientProbe.ref
        }))

        udpExtensionProbe.expectMsgType[Udp.Bind]
        udpExtensionProbe.lastSender ! Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325))

        client ! exampleRequest
        expectMsgType[Udp.Send]
        if (useTcp) {
          val truncated = exampleResponseMessage.copy(flags = MessageFlags(answer = true, truncated = true))
          client ! Udp.Received(truncated.write(), dnsServerAddress)
          tcpClientProbe.expectMsg(exampleRequestMessage)
        }

        client ! DropRequest(exampleRequest)
        expectMsg(Dropped(exampleRequest.id))

        client ! exampleRequest
        expectMsgType[Udp.Send]
        client ! Udp.Received(exampleResponseMessage.write(), dnsServerAddress)
        expectMsg(exampleResponse)
      }
    }

    "release UDP request state after a send failure" in {
      val udpExtensionProbe = TestProbe()
      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref
      }))

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpExtensionProbe.lastSender ! Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325))

      client ! exampleRequest
      val send = expectMsgType[Udp.Send]
      client ! Udp.CommandFailed(send)
      expectMsgType[Failure]

      client ! exampleRequest
      expectMsgType[Udp.Send]
      client ! Udp.Received(exampleResponseMessage.write(), dnsServerAddress)
      expectMsg(exampleResponse)
    }

    "Do not accept duplicate transaction ids" in {
      val udpExtensionProbe = TestProbe()
      val udpSocketProbe = TestProbe()
      val tcpClientProbe = TestProbe()
      val goodSenderProbe = TestProbe()
      val badSenderProbe = TestProbe()

      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient() = tcpClientProbe.ref
      }))
      val badRequest = Question4(exampleRequest.id, "not." + exampleRequest.name)

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpSocketProbe.send(
        udpExtensionProbe.lastSender,
        Udp.Bound(InetSocketAddress.createUnresolved("localhost", 41325)))

      goodSenderProbe.send(client, exampleRequest)

      val udpSend = udpSocketProbe.expectMsgType[Udp.Send]
      udpSend.payload shouldBe exampleRequestMessage.write()

      badSenderProbe.send(client, badRequest)
      badSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      val answer = UdpDnsAnswer(exampleRequest.id, im.Seq(), im.Seq())
      udpSocketProbe.reply(Udp.Received(exampleResponseMessage.write(), dnsServerAddress))
      goodSenderProbe.expectMsg(answer)

      udpSocketProbe.expectNoMessage()
    }

    "Verify original question when processing UDP replies (DNS poisoning)" in {
      val udpExtensionProbe = TestProbe()
      val udpSocketProbe = TestProbe()
      val tcpClientProbe = TestProbe()
      val goodSenderProbe = TestProbe()

      val socket = InetSocketAddress.createUnresolved("localhost", 41325)
      val goodRecord = ARecord(exampleRequest.name, CachePolicy.Ttl.never, InetAddress.getLocalHost())

      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient() = tcpClientProbe.ref
      }))

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpSocketProbe.send(udpExtensionProbe.lastSender, Udp.Bound(socket))

      goodSenderProbe.send(client, exampleRequest)

      val udpSend = udpSocketProbe.expectMsgType[Udp.Send]
      udpSend.payload shouldBe exampleRequestMessage.write()

      val flags = MessageFlags(true, authoritativeAnswer = true)
      val badId = exampleRequestMessage.copy(id = 999, flags = flags, answerRecs = im.Seq(goodRecord))
      val badQuestion = exampleRequestMessage.copy(
        flags = flags,
        questions = im.Seq(exampleQuestion.copy(name = "not.com")),
        answerRecs = im.Seq(goodRecord))
      val goodAnswer = exampleRequestMessage.copy(flags = flags, answerRecs = im.Seq(goodRecord))

      udpSocketProbe.reply(Udp.Received(internal.ByteResponse(badId), dnsServerAddress))
      udpSocketProbe.reply(Udp.Received(internal.ByteResponse(badQuestion), dnsServerAddress))
      udpSocketProbe.reply(Udp.Received(internal.ByteResponse(goodAnswer), dnsServerAddress))

      val answer = UdpDnsAnswer(exampleRequest.id, im.Seq(goodRecord), im.Seq())
      goodSenderProbe.expectMsg(answer)

      udpSocketProbe.expectNoMessage()
    }

    "Verify original question when processing DropRequest" in {
      val udpExtensionProbe = TestProbe()
      val udpSocketProbe = TestProbe()
      val tcpClientProbe = TestProbe()
      val goodSenderProbe = TestProbe()

      val socket = InetSocketAddress.createUnresolved("localhost", 41325)
      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient() = tcpClientProbe.ref
      }))

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpSocketProbe.send(udpExtensionProbe.lastSender, Udp.Bound(socket))

      goodSenderProbe.send(client, exampleRequest)

      val udpSend = udpSocketProbe.expectMsgType[Udp.Send]
      udpSend.payload shouldBe exampleRequestMessage.write()

      goodSenderProbe.send(client, DropRequest(exampleRequest.copy(id = 999)))
      goodSenderProbe.expectMsg(Dropped(999))

      // duplicate shows inflight message not deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      goodSenderProbe.send(client, DropRequest(exampleRequest.copy(name = "not.com")))

      // duplicate shows inflight message not deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      goodSenderProbe.send(client, DropRequest(Question6(exampleRequest.id, exampleRequest.name)))

      // duplicate shows inflight message not deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      goodSenderProbe.send(client, DropRequest(exampleRequest))
      goodSenderProbe.expectMsg(Dropped(exampleRequest.id))

      // no duplicate shows inflight message was deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectNoMessage()
    }

    "Ignore DNS responses from unexpected remotes" in {
      val udpExtensionProbe = TestProbe()
      val udpSocketProbe = TestProbe()
      val tcpClientProbe = TestProbe()
      val goodSenderProbe = TestProbe()

      val socket = InetSocketAddress.createUnresolved("localhost", 41325)
      val unexpectedAddress = InetSocketAddress.createUnresolved("bar", 53)
      val goodRecord = ARecord(exampleRequest.name, CachePolicy.Ttl.never, InetAddress.getLocalHost())

      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient() = tcpClientProbe.ref
      }))

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpSocketProbe.send(udpExtensionProbe.lastSender, Udp.Bound(socket))

      goodSenderProbe.send(client, exampleRequest)

      val udpSend = udpSocketProbe.expectMsgType[Udp.Send]
      udpSend.payload shouldBe exampleRequestMessage.write()

      val flags = MessageFlags(true, authoritativeAnswer = true)
      val goodAnswer = exampleRequestMessage.copy(flags = flags, answerRecs = im.Seq(goodRecord))

      udpSocketProbe.reply(Udp.Received(internal.ByteResponse(goodAnswer), unexpectedAddress))

      // duplicate shows unexpected remote did not complete or delete the inflight request
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      udpSocketProbe.reply(Udp.Received(internal.ByteResponse(goodAnswer), dnsServerAddress))

      val answer = UdpDnsAnswer(exampleRequest.id, im.Seq(goodRecord), im.Seq())
      goodSenderProbe.expectMsg(answer)
    }

    "Verify original question when processing UDP Failures" in {
      val udpExtensionProbe = TestProbe()
      val udpSocketProbe = TestProbe()
      val tcpClientProbe = TestProbe()
      val goodSenderProbe = TestProbe()

      val socket = InetSocketAddress.createUnresolved("localhost", 41325)
      val client = system.actorOf(Props(new DnsClient(dnsServerAddress) {
        override val udp = udpExtensionProbe.ref

        override def createTcpClient() = tcpClientProbe.ref
      }))

      udpExtensionProbe.expectMsgType[Udp.Bind]
      udpSocketProbe.send(udpExtensionProbe.lastSender, Udp.Bound(socket))

      goodSenderProbe.send(client, exampleRequest)

      val udpSend = udpSocketProbe.expectMsgType[Udp.Send]
      udpSend.payload shouldBe exampleRequestMessage.write()

      val badId = exampleRequestMessage.copy(id = 999)
      val badQuestion = exampleRequestMessage.copy(
        questions = im.Seq(exampleQuestion.copy(name = "not.com")))
      val goodQuestion = exampleRequestMessage

      udpSocketProbe.reply(Udp.CommandFailed(Udp.Send(internal.ByteResponse(badId), socket)))

      // duplicate shows inflight message not deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      udpSocketProbe.reply(Udp.CommandFailed(Udp.Send(internal.ByteResponse(badQuestion), socket)))

      // duplicate shows inflight message not deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      udpSocketProbe.reply(Udp.CommandFailed(Udp.Send(internal.ByteResponse(goodQuestion), socket)))
      goodSenderProbe.expectMsgType[Failure]

      // no duplicate shows inflight message was deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectNoMessage()
    }
  }
}

/**
 * The main code only knows how to write the questions not the responses, ByteResponse
 * implements just enough of the writing logic that the main code can parse the answers
 * messages used in tests.
 *
 * Message is only available to the internal package.
 */
package internal {

  import org.apache.pekko.io.dns.ResourceRecord
  import org.apache.pekko.util.{ ByteString, ByteStringBuilder }

  object ByteResponse {

    def apply(msg: Message): ByteString = {
      val ret = ByteString.newBuilder
      write(msg, ret)
      ret.result()
    }

    def write(msg: Message, ret: ByteStringBuilder): Unit = {
      ret
        .putShort(msg.id)
        .putShort(msg.flags.flags)
        .putShort(msg.questions.size)
        .putShort(msg.answerRecs.size)
        .putShort(0)
        .putShort(0)

      msg.questions.foreach(_.write(ret))
      msg.answerRecs.foreach(write(_, ret))
    }

    def write(msg: ResourceRecord, ret: ByteStringBuilder): Unit = {
      msg match {
        case ARecord(name, ttl, ip) =>
          DomainName.write(ret, name)
          ret.putShort(RecordType.A.code)
          ret.putShort(RecordClass.IN.code)
          ret.putInt(ttl.value.toSeconds.toInt)
          ret.putShort(4)
          ret.putBytes(ip.getAddress, 0, 4)
        case _ =>
          throw new IllegalStateException(s"Tests cannot write messages of type ${msg.getClass}")
      }
    }
  }
}
