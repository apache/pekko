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
import org.apache.pekko
import org.apache.pekko.actor.Status.Failure
import pekko.actor.Props
import pekko.io.Udp
import pekko.io.dns.{ ARecord, CachePolicy, RecordClass, RecordType }
import pekko.io.dns.internal.DnsClient.{ Answer, DropRequest, DuplicateId, Question4 }
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestProbe }

class DnsClientSpec extends PekkoSpec with ImplicitSender {
  "The async DNS client" should {
    val exampleRequest = Question4(42, "pekko.io")
    val exampleQuestion = Question("pekko.io", RecordType.A, RecordClass.IN)
    val exampleRequestMessage = Message(42, MessageFlags(), questions = im.Seq(exampleQuestion))
    val exampleResponseMessage = Message(42, MessageFlags(answer = true), questions = im.Seq(exampleQuestion))
    val exampleResponse = Answer(42, Nil)
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

    "Fall back to TCP when the UDP response is truncated" in {
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
      client ! Udp.Received(Message(exampleRequest.id, MessageFlags(truncated = true)).write(), dnsServerAddress)

      tcpClientProbe.expectMsg(exampleRequestMessage)
      tcpClientProbe.reply(exampleResponse)

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

      val answer = Answer(exampleRequest.id, im.Seq(), im.Seq())
      udpSocketProbe.reply(answer)
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

      udpSocketProbe.reply(Udp.Received(internal.ByteResponse(badId), socket))
      udpSocketProbe.reply(Udp.Received(internal.ByteResponse(badQuestion), socket))
      udpSocketProbe.reply(Udp.Received(internal.ByteResponse(goodAnswer), socket))

      val answer = Answer(exampleRequest.id, im.Seq(goodRecord), im.Seq())
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

      // duplicate shows inflight message not deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      goodSenderProbe.send(client, DropRequest(exampleRequest.copy(name = "not.com")))

      // duplicate shows inflight message not deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectMsg(DuplicateId(exampleRequest.id))

      goodSenderProbe.send(client, DropRequest(exampleRequest))

      // no duplicate shows inflight message was deleted
      goodSenderProbe.send(client, exampleRequest)
      goodSenderProbe.expectNoMessage()
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
