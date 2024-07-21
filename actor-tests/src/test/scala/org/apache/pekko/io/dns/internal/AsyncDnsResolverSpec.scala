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

import java.net.{ Inet6Address, InetAddress }

import scala.collection.{ immutable => im }
import scala.concurrent.duration._

import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.apache.pekko
import pekko.actor.{ ActorRef, ExtendedActorSystem, Props }
import pekko.actor.Status.Failure
import pekko.io.SimpleDnsCache
import pekko.io.dns.{ AAAARecord, ARecord, DnsSettings, IdGenerator, SRVRecord }
import pekko.io.dns.CachePolicy.Ttl
import pekko.io.dns.DnsProtocol._
import pekko.io.dns.internal.AsyncDnsResolver.ResolveFailedException
import pekko.io.dns.internal.DnsClient.{ Answer, DuplicateId, Question4, Question6, SrvQuestion }
import pekko.testkit.{ PekkoSpec, TestProbe, WithLogCapturing }

class AsyncDnsResolverSpec extends PekkoSpec("""
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
  """) with WithLogCapturing {

  val defaultConfig = ConfigFactory.parseString("""
          nameservers = ["one","two"]
          resolve-timeout = 300ms
          search-domains = []
          ndots = 1
          positive-ttl = forever
          negative-ttl = never
        """)

  trait Setup {
    val dnsClient1 = TestProbe()
    val dnsClient2 = TestProbe()
    val r = resolver(List(dnsClient1.ref, dnsClient2.ref), defaultConfig)
    val senderProbe = TestProbe()
    implicit val sender: ActorRef = senderProbe.ref
  }

  "Async DNS Resolver" must {
    "use dns clients in order" in new Setup {
      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val id = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Answer(id, im.Seq.empty))
      dnsClient2.expectNoMessage()
      senderProbe.expectMsg(Resolved("cats.com", im.Seq.empty))
    }

    "move to next client if first fails" in new Setup {
      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      // first will get ask timeout
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Failure(new RuntimeException("Nope")))
      val secondId = dnsClient2.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" && q4.id != firstId =>
          q4.id
      }
      dnsClient2.reply(Answer(secondId, im.Seq.empty))
      senderProbe.expectMsg(Resolved("cats.com", im.Seq.empty))
    }

    "move to next client if first times out" in new Setup {
      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      // first will get ask timeout
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      val secondId = dnsClient2.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" && q4.id != firstId =>
          q4.id
      }
      dnsClient2.reply(Answer(secondId, im.Seq.empty))
      senderProbe.expectMsg(Resolved("cats.com", im.Seq.empty))
    }

    "handle duplicate Ids in dnsClient" in new Setup {
      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(DuplicateId(firstId))
      val secondId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" && q4.id != firstId =>
          q4.id
      }
      dnsClient1.reply(Answer(secondId, im.Seq.empty))
      dnsClient2.expectNoMessage()
      senderProbe.expectMsg(Resolved("cats.com", im.Seq.empty))
    }

    "gets both A and AAAA records if requested" in new Setup {
      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = true))
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      val ttl = Ttl.fromPositive(100.seconds)
      val ipv4Record = ARecord("cats.com", ttl, InetAddress.getByName("127.0.0.1"))
      dnsClient1.reply(Answer(firstId, im.Seq(ipv4Record)))
      val secondId = dnsClient1.expectMsgPF() {
        case q6: Question6 if q6.name == "cats.com" && q6.id != firstId =>
          q6.id
      }
      val ipv6Record = AAAARecord("cats.com", ttl, InetAddress.getByName("::1").asInstanceOf[Inet6Address])
      dnsClient1.reply(Answer(secondId, im.Seq(ipv6Record)))
      senderProbe.expectMsg(Resolved("cats.com", im.Seq(ipv4Record, ipv6Record)))
    }

    "fails if all dns clients timeout" in new Setup {
      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      senderProbe.expectMsgPF(remainingOrDefault) {
        case Failure(ResolveFailedException(_)) =>
      }
    }

    "fails if all dns clients fail" in new Setup {
      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Failure(new RuntimeException("Fail")))
      dnsClient2.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" && q4.id != firstId =>
          q4.id
      }
      dnsClient2.reply(Failure(new RuntimeException("Yet another fail")))
      senderProbe.expectMsgPF(remainingOrDefault) {
        case Failure(ResolveFailedException(_)) =>
      }
    }

    "gets SRV records if requested" in new Setup {
      r ! Resolve("cats.com", Srv)
      val firstId = dnsClient1.expectMsgPF() {
        case srvQuestion: SrvQuestion if srvQuestion.name == "cats.com" =>
          srvQuestion.id
      }
      dnsClient1.reply(Answer(firstId, im.Seq.empty))
      dnsClient2.expectNoMessage()
      senderProbe.expectMsg(Resolved("cats.com", im.Seq.empty))
    }

    "response immediately IP address" in new Setup {
      val name = "127.0.0.1"
      r ! Resolve(name)
      dnsClient1.expectNoMessage(50.millis)
      val answer = senderProbe.expectMsgType[Resolved]
      answer.records.collect { case r: ARecord => r }.toSet shouldEqual Set(
        ARecord("127.0.0.1", Ttl.effectivelyForever, InetAddress.getByName("127.0.0.1")))
    }

    "response immediately for IPv6 address" in new Setup {
      val name = "1:2:3:0:0:0:0:0"
      r ! Resolve(name)
      dnsClient1.expectNoMessage(50.millis)
      val answer = senderProbe.expectMsgType[Resolved]
      val aaaaRecord = answer.records match {
        case Seq(r: AAAARecord) => r
        case _                  => throw new RuntimeException() // compiler exhaustiveness check pleaser
      }
      aaaaRecord.name should be("1:2:3:0:0:0:0:0")
      aaaaRecord.ttl should be(Ttl.effectivelyForever)
      // The leading '/' indicates no reverse lookup was performed
      aaaaRecord.ip.toString should be("/1:2:3:0:0:0:0:0")
    }

    "return additional records for SRV requests" in new Setup {
      r ! Resolve("cats.com", Srv)
      val firstId = dnsClient1.expectMsgPF() {
        case srvQuestion: SrvQuestion if srvQuestion.name == "cats.com" =>
          srvQuestion.id
      }
      val srvRecs = im.Seq(SRVRecord("cats.com", Ttl.fromPositive(5000.seconds), 1, 1, 1, "a.cats.com"))
      val aRecs = im.Seq(ARecord("a.cats.com", Ttl.fromPositive(1.seconds), InetAddress.getByName("127.0.0.1")))
      dnsClient1.reply(Answer(firstId, srvRecs, aRecs))
      dnsClient2.expectNoMessage(50.millis)
      senderProbe.expectMsg(Resolved("cats.com", srvRecs, aRecs))

      // cached the second time, don't have the probe reply
      r ! Resolve("cats.com", Srv)
      senderProbe.expectMsg(Resolved("cats.com", srvRecs, aRecs))
    }

    "don't use resolver in failure case if negative-ttl != never" in new Setup {
      val configWithSmallTtl = defaultConfig.withValue("negative-ttl", ConfigValueFactory.fromAnyRef("5s"))
      override val r = resolver(List(dnsClient1.ref), configWithSmallTtl)

      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Answer(firstId, im.Seq.empty))

      senderProbe.expectMsg(Resolved("cats.com", im.Seq()))

      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      dnsClient1.expectNoMessage(50.millis)

      senderProbe.expectMsg(Resolved("cats.com", im.Seq()))
    }

    "don't use resolver until record in cache will expired" in new Setup {
      val recordTtl = Ttl.fromPositive(100.seconds)
      val ipv4Record = ARecord("cats.com", recordTtl, InetAddress.getByName("127.0.0.1"))

      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Answer(firstId, im.Seq(ipv4Record)))

      senderProbe.expectMsg(Resolved("cats.com", im.Seq(ipv4Record)))

      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      dnsClient1.expectNoMessage(50.millis)

      senderProbe.expectMsg(Resolved("cats.com", im.Seq(ipv4Record)))
    }

    "always use resolver if positive-ttl = never" in new Setup {
      val configWithSmallTtl = defaultConfig.withValue("positive-ttl", ConfigValueFactory.fromAnyRef("never"))
      override val r = resolver(List(dnsClient1.ref), configWithSmallTtl)
      val recordTtl = Ttl.fromPositive(100.seconds)

      val ipv4Record = ARecord("cats.com", recordTtl, InetAddress.getByName("127.0.0.1"))

      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Answer(firstId, im.Seq(ipv4Record)))

      senderProbe.expectMsg(Resolved("cats.com", im.Seq(ipv4Record)))

      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val secondId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Answer(secondId, im.Seq(ipv4Record)))

      senderProbe.expectMsg(Resolved("cats.com", im.Seq(ipv4Record)))
    }

    "don't use resolver until cache record will be expired" in new Setup {
      val configWithSmallTtl = defaultConfig.withValue("positive-ttl", ConfigValueFactory.fromAnyRef("200 millis"))
      override val r = resolver(List(dnsClient1.ref), configWithSmallTtl)
      val recordTtl = Ttl.fromPositive(100.seconds)

      val ipv4Record = ARecord("cats.com", recordTtl, InetAddress.getByName("127.0.0.1"))

      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val firstId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Answer(firstId, im.Seq(ipv4Record)))
      senderProbe.expectMsg(Resolved("cats.com", im.Seq(ipv4Record)))

      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      dnsClient1.expectNoMessage(50.millis)

      senderProbe.expectMsg(Resolved("cats.com", im.Seq(ipv4Record)))

      Thread.sleep(200)
      r ! Resolve("cats.com", Ip(ipv4 = true, ipv6 = false))
      val secondId = dnsClient1.expectMsgPF() {
        case q4: Question4 if q4.name == "cats.com" =>
          q4.id
      }
      dnsClient1.reply(Answer(secondId, im.Seq(ipv4Record)))

      senderProbe.expectMsg(Resolved("cats.com", im.Seq(ipv4Record)))
    }
  }

  def resolver(clients: List[ActorRef], config: Config): ActorRef = {
    val settings = new DnsSettings(system.asInstanceOf[ExtendedActorSystem], config)
    system.actorOf(Props(new AsyncDnsResolver(settings, new SimpleDnsCache(),
      (_, _) =>
        clients, IdGenerator())))
  }
}
