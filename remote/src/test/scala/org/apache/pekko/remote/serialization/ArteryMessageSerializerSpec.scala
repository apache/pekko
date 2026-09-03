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

package org.apache.pekko.remote.serialization

import java.io.NotSerializableException

import org.apache.pekko
import pekko.actor._
import pekko.remote.{ ArteryControlFormats, RemoteWatcher, UniqueAddress }
import pekko.remote.artery.{ ActorSystemTerminating, ActorSystemTerminatingAck, Quarantined, SystemMessageDelivery }
import pekko.remote.artery.Flush
import pekko.remote.artery.FlushAck
import pekko.remote.artery.OutboundHandshake.{ HandshakeReq, HandshakeRsp }
import pekko.remote.artery.compress.CompressionProtocol.{
  ActorRefCompressionAdvertisement,
  ActorRefCompressionAdvertisementAck,
  ClassManifestCompressionAdvertisement,
  ClassManifestCompressionAdvertisementAck
}
import pekko.remote.artery.compress.CompressionTable
import pekko.serialization.SerializationExtension
import pekko.testkit.PekkoSpec

class ArteryMessageSerializerSpec extends PekkoSpec {
  "ArteryMessageSerializer" must {
    val actorA = system.actorOf(Props.empty)
    val actorB = system.actorOf(Props.empty)

    Seq(
      "Quarantined" -> Quarantined(uniqueAddress(), uniqueAddress()),
      "ActorSystemTerminating" -> ActorSystemTerminating(uniqueAddress()),
      "ActorSystemTerminatingAck" -> ActorSystemTerminatingAck(uniqueAddress()),
      "Flush" -> Flush,
      "FlushAck" -> FlushAck(3),
      "HandshakeReq" -> HandshakeReq(uniqueAddress(), uniqueAddress().address),
      "HandshakeRsp" -> HandshakeRsp(uniqueAddress()),
      "ActorRefCompressionAdvertisement" -> ActorRefCompressionAdvertisement(
        uniqueAddress(),
        CompressionTable(17L, 123, Map(actorA -> 123, actorB -> 456, system.deadLetters -> 0))),
      "ActorRefCompressionAdvertisementAck" -> ActorRefCompressionAdvertisementAck(uniqueAddress(), 23),
      "ClassManifestCompressionAdvertisement" -> ClassManifestCompressionAdvertisement(
        uniqueAddress(),
        CompressionTable(17L, 42, Map("a" -> 535, "b" -> 23))),
      "ClassManifestCompressionAdvertisementAck" -> ClassManifestCompressionAdvertisementAck(uniqueAddress(), 23),
      "SystemMessageDelivery.SystemMessageEnvelop" -> SystemMessageDelivery.SystemMessageEnvelope(
        "test",
        1234567890123L,
        uniqueAddress()),
      "SystemMessageDelivery.Ack" -> SystemMessageDelivery.Ack(98765432109876L, uniqueAddress()),
      "SystemMessageDelivery.Nack" -> SystemMessageDelivery.Nack(98765432109876L, uniqueAddress()),
      "RemoteWatcher.ArteryHeartbeat" -> RemoteWatcher.ArteryHeartbeat,
      "RemoteWatcher.ArteryHeartbeatRsp" -> RemoteWatcher.ArteryHeartbeatRsp(Long.MaxValue)).foreach {
      case (scenario, item) =>
        s"resolve serializer for $scenario" in {
          val serializer = SerializationExtension(system)
          serializer.serializerFor(item.getClass).getClass should ===(classOf[ArteryMessageSerializer])
        }

        s"serialize and de-serialize $scenario" in {
          verifySerialization(item)
        }
    }

    "not support UniqueAddresses without host/port set" in pending

    "reject a compression table advertisement whose keys and values disagree in length" in {
      val serializer = new ArteryMessageSerializer(system.asInstanceOf[ExtendedActorSystem])
      val bytes = ArteryControlFormats.CompressionTableAdvertisement.newBuilder
        .setFrom(serializer.serializeUniqueAddress(uniqueAddress()))
        .setOriginUid(17L)
        .setTableVersion(1)
        .addKeys("a")
        .addKeys("b")
        .addValues(0)
        .build()
        .toByteArray

      intercept[NotSerializableException] {
        serializer.fromBinary(bytes, "h") // ClassManifestCompressionAdvertisement
      }.getMessage should include("must match")
    }

    "reject a compression table version that does not fit in a byte" in {
      val serializer = new ArteryMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

      // the version is a Byte on both sides, so 128 is not a version any peer advertised;
      // narrowing it silently would make it indistinguishable from -128
      val advertisement = ArteryControlFormats.CompressionTableAdvertisement.newBuilder
        .setFrom(serializer.serializeUniqueAddress(uniqueAddress()))
        .setOriginUid(17L)
        .setTableVersion(128)
        .build()
        .toByteArray
      intercept[NotSerializableException] {
        serializer.fromBinary(advertisement, "h")
      }.getMessage should include("outside the range")

      val ack = ArteryControlFormats.CompressionTableAdvertisementAck.newBuilder
        .setFrom(serializer.serializeUniqueAddress(uniqueAddress()))
        .setVersion(128)
        .build()
        .toByteArray
      intercept[NotSerializableException] {
        serializer.fromBinary(ack, "i") // ClassManifestCompressionAdvertisementAck
      }.getMessage should include("outside the range")
    }

    "accept the whole byte range of compression table versions" in {
      val serializer = new ArteryMessageSerializer(system.asInstanceOf[ExtendedActorSystem])
      Seq[Byte](Byte.MinValue, -1, 0, 1, Byte.MaxValue).foreach { version =>
        withClue(s"version $version: ") {
          val msg = ClassManifestCompressionAdvertisementAck(uniqueAddress(), version)
          serializer.fromBinary(serializer.toBinary(msg), serializer.manifest(msg)) should ===(msg)
        }
      }
    }

    "reject invalid manifest" in {
      intercept[IllegalArgumentException] {
        val serializer = new ArteryMessageSerializer(system.asInstanceOf[ExtendedActorSystem])
        serializer.manifest("INVALID")
      }
    }

    "reject deserialization with invalid manifest" in {
      intercept[NotSerializableException] {
        val serializer = new ArteryMessageSerializer(system.asInstanceOf[ExtendedActorSystem])
        serializer.fromBinary(Array.empty[Byte], "INVALID")
      }
    }

    def verifySerialization(msg: AnyRef): Unit = {
      val serializer = new ArteryMessageSerializer(system.asInstanceOf[ExtendedActorSystem])
      serializer.fromBinary(serializer.toBinary(msg), serializer.manifest(msg)) should ===(msg)
    }

    def uniqueAddress(): UniqueAddress =
      UniqueAddress(Address("abc", "def", "host", 12345), 2342)
  }
}
