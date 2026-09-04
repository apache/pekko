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

package org.apache.pekko.cluster.protobuf

import java.io.NotSerializableException

import collection.immutable.SortedSet

import scala.annotation.nowarn
import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ Address, ExtendedActorSystem }
import pekko.cluster._
import pekko.cluster.InternalClusterAction.CompatibleConfig
import pekko.cluster.protobuf.msg.{ ClusterMessages => cm }
import pekko.cluster.routing.{ ClusterRouterPool, ClusterRouterPoolSettings }
import pekko.remote.ByteStringUtils
import pekko.routing.RoundRobinPool
import pekko.testkit.PekkoSpec
import pekko.util.Version

@nowarn
class ClusterMessageSerializerSpec extends PekkoSpec("pekko.actor.provider = cluster") {

  val serializer = new ClusterMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  def roundtrip[T <: AnyRef](obj: T): T = {
    val manifest = serializer.manifest(obj)
    val blob = serializer.toBinary(obj)
    serializer.fromBinary(blob, manifest).asInstanceOf[T]
  }

  def checkSerialization(obj: AnyRef): Unit = {
    (obj, roundtrip(obj)) match {
      case (env: GossipEnvelope, env2: GossipEnvelope) =>
        env2.from should ===(env.from)
        env2.to should ===(env.to)
        env2.gossip should ===(env.gossip)
      case (_, ref) =>
        ref should ===(obj)
    }
  }

  private def roundtripWithManifest[T <: AnyRef](obj: T, manifest: String): T = {
    val blob = serializer.toBinary(obj)
    serializer.fromBinary(blob, manifest).asInstanceOf[T]
  }

  private def checkDeserializationWithManifest(obj: AnyRef, deserializationManifest: String): Unit = {
    (obj, roundtripWithManifest(obj, deserializationManifest)) match {
      case (env: GossipEnvelope, env2: GossipEnvelope) =>
        env2.from should ===(env.from)
        env2.to should ===(env.to)
        env2.gossip should ===(env.gossip)
        env.gossip.members.foreach { m1 =>
          val m2 = env.gossip.members.find(_.uniqueAddress == m1.uniqueAddress).get
          checkSameMember(m1, m2)
        }
      case (_, ref) =>
        ref should ===(obj)
    }
  }

  private def checkSameMember(m1: Member, m2: Member): Unit = {
    m1.uniqueAddress should ===(m2.uniqueAddress)
    m1.status should ===(m2.status)
    m1.appVersion should ===(m2.appVersion)
    m1.dataCenter should ===(m2.dataCenter)
    m1.roles should ===(m2.roles)
    m1.upNumber should ===(m2.upNumber)
  }

  import MemberStatus._

  val a1 =
    TestMember(Address("pekko", "sys", "a", 7355), Joining, Set.empty[String], appVersion = Version("1.0.0"))
  val b1 = TestMember(Address("pekko", "sys", "b", 7355), Up, Set("r1"), appVersion = Version("1.1.0"))
  val c1 =
    TestMember(Address("pekko", "sys", "c", 7355), Leaving, Set.empty[String], "foo", appVersion = Version("1.1.0"))
  val d1 = TestMember(Address("pekko", "sys", "d", 7355), Exiting, Set("r1"), "foo")
  val e1 = TestMember(Address("pekko", "sys", "e", 7355), Down, Set("r3"))
  val f1 = TestMember(Address("pekko", "sys", "f", 7355), Removed, Set("r3"), "foo")

  "ClusterMessages" must {

    "be serializable" in {
      val address = Address("pekko", "system", "some.host.org", 4711)
      val uniqueAddress = UniqueAddress(address, 17L)
      val address2 = Address("pekko", "system", "other.host.org", 4711)
      val uniqueAddress2 = UniqueAddress(address2, 18L)
      checkSerialization(InternalClusterAction.Join(uniqueAddress, Set("foo", "bar", "dc-A"), Version.Zero))
      checkSerialization(InternalClusterAction.Join(uniqueAddress, Set("dc-A"), Version("1.2.3")))
      checkSerialization(ClusterUserAction.Leave(address))
      checkSerialization(ClusterUserAction.Down(address))
      checkSerialization(InternalClusterAction.InitJoin(ConfigFactory.empty))
      checkSerialization(InternalClusterAction.InitJoinAck(address, CompatibleConfig(ConfigFactory.empty)))
      checkSerialization(InternalClusterAction.InitJoinNack(address))
      checkSerialization(ClusterHeartbeatSender.Heartbeat(address, -1, -1))
      checkSerialization(ClusterHeartbeatSender.HeartbeatRsp(uniqueAddress, -1, -1))
      checkSerialization(InternalClusterAction.ExitingConfirmed(uniqueAddress))

      val node1 = VectorClock.Node("node1")
      val node2 = VectorClock.Node("node2")
      val node3 = VectorClock.Node("node3")
      val node4 = VectorClock.Node("node4")
      val g1 = (Gossip(SortedSet(a1, b1, c1, d1)) :+ node1 :+ node2).seen(a1.uniqueAddress).seen(b1.uniqueAddress)
      val g2 = (g1 :+ node3 :+ node4).seen(a1.uniqueAddress).seen(c1.uniqueAddress)
      val reachability3 = Reachability.empty
        .unreachable(a1.uniqueAddress, e1.uniqueAddress)
        .unreachable(b1.uniqueAddress, e1.uniqueAddress)
      val g3 =
        g2.copy(members = SortedSet(a1, b1, c1, d1, e1), overview = g2.overview.copy(reachability = reachability3))
      val g4 = g1.remove(d1.uniqueAddress, 352684800)
      checkSerialization(GossipEnvelope(a1.uniqueAddress, uniqueAddress2, g1))
      checkSerialization(GossipEnvelope(a1.uniqueAddress, uniqueAddress2, g2))
      checkSerialization(GossipEnvelope(a1.uniqueAddress, uniqueAddress2, g3))
      checkSerialization(GossipEnvelope(a1.uniqueAddress, uniqueAddress2, g4))

      checkSerialization(GossipStatus(a1.uniqueAddress, g1.version, g1.seenDigest))
      checkSerialization(GossipStatus(a1.uniqueAddress, g2.version, g2.seenDigest))
      checkSerialization(GossipStatus(a1.uniqueAddress, g3.version, g3.seenDigest))

      checkSerialization(InternalClusterAction.Welcome(uniqueAddress, g2))
    }

    // can be removed in Akka 2.6.3 only checks deserialization with new not yet in effect manifests for Akka 2.6.2
    "be de-serializable with class manifests from Akka 2.6.4 and earlier nodes" in {
      val address = Address("pekko", "system", "some.host.org", 4711)
      val uniqueAddress = UniqueAddress(address, 17L)
      val address2 = Address("pekko", "system", "other.host.org", 4711)
      val uniqueAddress2 = UniqueAddress(address2, 18L)
      checkDeserializationWithManifest(
        InternalClusterAction.Join(uniqueAddress, Set("foo", "bar", "dc-A"), Version.Zero),
        ClusterMessageSerializer.OldJoinManifest)
      checkDeserializationWithManifest(ClusterUserAction.Leave(address), ClusterMessageSerializer.LeaveManifest)
      checkDeserializationWithManifest(ClusterUserAction.Down(address), ClusterMessageSerializer.DownManifest)
      checkDeserializationWithManifest(
        InternalClusterAction.InitJoin(ConfigFactory.empty),
        ClusterMessageSerializer.OldInitJoinManifest)
      checkDeserializationWithManifest(
        InternalClusterAction.InitJoinAck(address, CompatibleConfig(ConfigFactory.empty)),
        ClusterMessageSerializer.OldInitJoinAckManifest)
      checkDeserializationWithManifest(
        InternalClusterAction.InitJoinNack(address),
        ClusterMessageSerializer.OldInitJoinNackManifest)
      checkDeserializationWithManifest(
        InternalClusterAction.ExitingConfirmed(uniqueAddress),
        ClusterMessageSerializer.OldExitingConfirmedManifest)

      val node1 = VectorClock.Node("node1")
      val node2 = VectorClock.Node("node2")
      val node3 = VectorClock.Node("node3")
      val node4 = VectorClock.Node("node4")
      val g1 = (Gossip(SortedSet(a1, b1, c1, d1)) :+ node1 :+ node2).seen(a1.uniqueAddress).seen(b1.uniqueAddress)
      val g2 = (g1 :+ node3 :+ node4).seen(a1.uniqueAddress).seen(c1.uniqueAddress)
      val reachability3 = Reachability.empty
        .unreachable(a1.uniqueAddress, e1.uniqueAddress)
        .unreachable(b1.uniqueAddress, e1.uniqueAddress)
      checkDeserializationWithManifest(
        GossipEnvelope(a1.uniqueAddress, uniqueAddress2, g1),
        ClusterMessageSerializer.OldGossipEnvelopeManifest)

      checkDeserializationWithManifest(
        GossipStatus(a1.uniqueAddress, g1.version, g1.seenDigest),
        ClusterMessageSerializer.OldGossipStatusManifest)

      checkDeserializationWithManifest(
        InternalClusterAction.Welcome(uniqueAddress, g2),
        ClusterMessageSerializer.OldWelcomeManifest)
    }

    "reject gossip that refers to a lookup table entry it did not send" in {
      // Gossip interns addresses, roles, hashes and app versions and refers to them by index.
      // Every index gossipToProto writes is in range, so an out of range one is a message no
      // toBinary produced; it must not surface as IndexOutOfBoundsException.
      val node1 = VectorClock.Node("node1")
      val gossip = (Gossip(SortedSet(a1, b1)) :+ node1).seen(a1.uniqueAddress)
      val welcome = InternalClusterAction.Welcome(a1.uniqueAddress, gossip)
      val proto = cm.Welcome.parseFrom(serializer.decompress(serializer.toBinary(welcome)))

      def rejects(tamper: cm.Gossip.Builder => Unit): String = {
        val g = proto.getGossip.toBuilder
        tamper(g)
        val bytes = serializer.compress(proto.toBuilder.setGossip(g).build())
        intercept[NotSerializableException](serializer.fromBinary(bytes, "W")).getMessage
      }

      // an address index one past the end of allAddresses
      rejects(_.setMembers(0, proto.getGossip.getMembers(0).toBuilder.setAddressIndex(99))) should include(
        "address index [99]")
      // a negative index
      rejects(_.setMembers(0, proto.getGossip.getMembers(0).toBuilder.setAddressIndex(-1))) should include(
        "address index [-1]")
      // a role index out of range
      rejects(_.setMembers(0, proto.getGossip.getMembers(0).toBuilder.setRolesIndexes(0, 99))) should include(
        "role index [99]")
      // a seen entry pointing nowhere
      rejects(_.setOverview(proto.getGossip.getOverview.toBuilder.setSeen(0, 99))) should include("address index [99]")
      // a vector clock hash index pointing nowhere
      rejects(
        _.setVersion(
          proto.getGossip.getVersion.toBuilder
            .setVersions(0, proto.getGossip.getVersion.getVersions(0).toBuilder.setHashIndex(99)))) should include(
        "hash index [99]")
    }

    "reject a gossip status that refers to a hash it did not send" in {
      val node1 = VectorClock.Node("node1")
      val gossip = Gossip(SortedSet(a1)) :+ node1
      val status = GossipStatus(a1.uniqueAddress, gossip.version, gossip.seenDigest)
      val proto = cm.GossipStatus.parseFrom(serializer.toBinary(status))

      val tampered = proto.toBuilder
        .setVersion(proto.getVersion.toBuilder.setVersions(0,
          proto.getVersion.getVersions(0).toBuilder.setHashIndex(7)))
        .build()
        .toByteArray

      intercept[NotSerializableException] {
        serializer.fromBinary(tampered, "GS")
      }.getMessage should include("hash index [7]")
    }

    "reject a gossip envelope with a bad index when the gossip is read" in {
      // GossipEnvelope defers the parse, so the failure surfaces from `gossip` rather than from
      // fromBinary - on the cluster daemon's thread rather than a deserialization thread.
      val gossip = Gossip(SortedSet(a1, b1))
      val envelope = GossipEnvelope(a1.uniqueAddress, b1.uniqueAddress, gossip)
      val proto = cm.GossipEnvelope.parseFrom(serializer.toBinary(envelope))
      val inner = cm.Gossip.parseFrom(serializer.decompress(proto.getSerializedGossip.toByteArray))
      val tamperedInner =
        inner.toBuilder.setMembers(0, inner.getMembers(0).toBuilder.setAddressIndex(99)).build()

      val bytes = proto.toBuilder
        .setSerializedGossip(ByteStringUtils.toProtoByteStringUnsafe(serializer.compress(tamperedInner)))
        .build()
        .toByteArray

      val msg = serializer.fromBinary(bytes, "GE").asInstanceOf[GossipEnvelope]
      intercept[NotSerializableException] {
        msg.gossip
      }.getMessage should include("address index [99]")
    }

    "add a default data center role to gossip if none is present" in {
      val env = roundtrip(GossipEnvelope(a1.uniqueAddress, d1.uniqueAddress, Gossip(SortedSet(a1, d1))))
      env.gossip.members.head.roles should be(Set(ClusterSettings.DcRolePrefix + "default"))
      env.gossip.members.tail.head.roles should be(Set("r1", ClusterSettings.DcRolePrefix + "foo"))
    }

    "not resolve includes in the config of a join message" in {
      // The joining node renders its config with ConfigRenderOptions.concise, which is JSON and
      // cannot carry an include, so nothing legitimate is lost by refusing to resolve one. An
      // include that did resolve would read a local file or issue an outbound request while
      // deserializing a message from a node that has not joined yet.
      val secretFile = Files.createTempFile("cluster-message-serializer-spec", ".conf")
      try {
        Files.write(secretFile, """secret = "leaked"""".getBytes(StandardCharsets.UTF_8))
        val hocon = s"""include file("${secretFile.toAbsolutePath}")
          pekko.cluster.roles = []"""

        val initJoin = serializer
          .fromBinary(cm.InitJoin.newBuilder().setCurrentConfig(hocon).build().toByteArray, "IJ")
          .asInstanceOf[InternalClusterAction.InitJoin]
        initJoin.configOfJoiningNode.hasPath("secret") should ===(false)

        val ackBytes = cm.InitJoinAck
          .newBuilder()
          .setAddress(serializer.addressToProto(Address("pekko", "system", "some.host.org", 4711)))
          .setConfigCheck(
            cm.ConfigCheck
              .newBuilder()
              .setType(cm.ConfigCheck.Type.CompatibleConfig)
              .setClusterConfig(hocon))
          .build()
          .toByteArray
        val ack = serializer.fromBinary(ackBytes, "IJA").asInstanceOf[InternalClusterAction.InitJoinAck]
        ack.configCheck.asInstanceOf[CompatibleConfig].clusterConfig.hasPath("secret") should ===(false)
      } finally Files.deleteIfExists(secretFile)
    }

    "add a default data center role to internal join action if none is present" in {
      val join = roundtrip(InternalClusterAction.Join(a1.uniqueAddress, Set(), Version.Zero))
      join.roles should be(Set(ClusterSettings.DcRolePrefix + "default"))
    }
  }

  // support for deserializing a new format with a string based manifest was added in Akka 2.5.23 but the next step
  // was never done, meaning that 2.6.4 still emits the old format
  "Rolling upgrades for heart beat message changes in Akka 2.5.23" must {

    "deserialize heart beats represented by just an address Address to support versions prior to Akka 2.6.5" in {
      val serialized = serializer.addressToProto(a1.address).build().toByteArray
      val deserialized = serializer.fromBinary(serialized, ClusterMessageSerializer.HeartBeatManifestPre2523)
      deserialized should ===(ClusterHeartbeatSender.Heartbeat(a1.address, -1, -1))
    }

    "deserialize heart beat responses as UniqueAddress to support versions prior to 2.5.23" in {
      val serialized = serializer.uniqueAddressToProto(a1.uniqueAddress).build().toByteArray
      val deserialized = serializer.fromBinary(serialized, ClusterMessageSerializer.HeartBeatRspManifest2523)
      deserialized should ===(ClusterHeartbeatSender.HeartbeatRsp(a1.uniqueAddress, -1, -1))
    }
  }

  "Cluster router pool" must {
    "be serializable with no role" in {
      checkSerialization(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 4),
          ClusterRouterPoolSettings(totalInstances = 2, maxInstancesPerNode = 5, allowLocalRoutees = true)))
    }

    "be serializable with one role" in {
      checkSerialization(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 4),
          ClusterRouterPoolSettings(
            totalInstances = 2,
            maxInstancesPerNode = 5,
            allowLocalRoutees = true,
            useRoles = Set("Richard, Duke of Gloucester"))))
    }

    "be serializable with many roles" in {
      checkSerialization(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 4),
          ClusterRouterPoolSettings(
            totalInstances = 2,
            maxInstancesPerNode = 5,
            allowLocalRoutees = true,
            useRoles = Set("Richard, Duke of Gloucester", "Hongzhi Emperor", "Red Rackham"))))
    }
  }

}
