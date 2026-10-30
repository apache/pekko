/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.cluster.ddata

import org.apache.pekko
import pekko.actor.Address
import pekko.cluster.Cluster
import pekko.cluster.ClusterEvent.MemberDowned
import pekko.cluster.ClusterEvent.MemberEvent
import pekko.cluster.ClusterEvent.MemberExited
import pekko.cluster.ClusterEvent.MemberJoined
import pekko.cluster.ClusterEvent.MemberLeft
import pekko.cluster.ClusterEvent.MemberPreparingForShutdown
import pekko.cluster.ClusterEvent.MemberReadyForShutdown
import pekko.cluster.ClusterEvent.MemberRemoved
import pekko.cluster.ClusterEvent.MemberUp
import pekko.cluster.ClusterEvent.MemberWeaklyUp
import pekko.cluster.ClusterSettings
import pekko.cluster.Member
import pekko.cluster.MemberStatus
import pekko.cluster.UniqueAddress
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe
import pekko.util.Version

import com.typesafe.config.ConfigFactory

class ReplicatorKnownNodeSpec
    extends PekkoSpec(ConfigFactory.parseString("""
      pekko.actor.provider = cluster
      pekko.remote.artery.canonical.port = 0
      pekko.cluster.roles = [replica]
    """)) {

  import Replicator.Internal

  private val cluster = Cluster(system)

  override protected def atStartup(): Unit = {
    cluster.join(cluster.selfAddress)
    awaitAssert(cluster.selfMember.status shouldEqual MemberStatus.Up)
  }

  private def memberOn(port: Int): Member =
    Member(
      UniqueAddress(Address("pekko", system.name, "127.0.0.1", port), port.toLong),
      Set("replica", ClusterSettings.DcRolePrefix + cluster.settings.SelfDataCenter),
      Version.Zero)

  private class TestSetup {
    val probe = TestProbe()
    val replicator = system.actorOf(Replicator.props(ReplicatorSettings(system).withRole("replica")))
    probe.send(replicator, Replicator.GetReplicaCount)
    probe.expectMsg(Replicator.ReplicaCount(1))

    def run(test: => Unit): Unit =
      try test
      finally {
        val terminationProbe = TestProbe()
        terminationProbe.watch(replicator)
        system.stop(replicator)
        terminationProbe.expectTerminated(replicator)
      }

    def writeAndReadFrom(member: Member, keyId: String): Unit = {
      val counter = GCounter.empty.increment(member.uniqueAddress, 1)
      probe.send(replicator, Internal.Write(keyId, Internal.DataEnvelope(counter), Some(member.uniqueAddress)))
      probe.expectMsg(Internal.WriteAck)
      probe.send(replicator, Internal.Read(keyId, Some(member.uniqueAddress)))
      probe.expectMsgType[Internal.ReadResult].envelope.map(_.data) shouldEqual Some(counter)
    }

    def deltaFrom(member: Member, keyId: String): Unit = {
      val key = GCounterKey(keyId)
      val counter = GCounter.empty.increment(member.uniqueAddress, 3)
      probe.send(
        replicator,
        Internal.DeltaPropagation(
          member.uniqueAddress,
          reply = true,
          Map(key.id -> Internal.Delta(Internal.DataEnvelope(counter.delta.get), 1L, 1L))))
      probe.expectMsg(Internal.WriteAck)
      probe.send(replicator, Replicator.Get(key, Replicator.ReadLocal))
      probe.expectMsgType[Replicator.GetSuccess[GCounter]].get(key) shouldEqual counter
    }

    def rejectReplicationFrom(member: Member, keyId: String): Unit = {
      val writeKey = GCounterKey(keyId + "-write")
      val deltaKey = GCounterKey(keyId + "-delta")
      val counter = GCounter.empty.increment(member.uniqueAddress, 1)
      probe.send(replicator, Internal.Read(writeKey.id, Some(member.uniqueAddress)))
      probe.send(
        replicator,
        Internal.Write(writeKey.id, Internal.DataEnvelope(counter), Some(member.uniqueAddress)))
      probe.send(
        replicator,
        Internal.DeltaPropagation(
          member.uniqueAddress,
          reply = true,
          Map(deltaKey.id -> Internal.Delta(Internal.DataEnvelope(counter.delta.get), 1L, 1L))))
      // Local reads from the same sender are barriers: ignored messages must neither reply nor store data.
      probe.send(replicator, Replicator.Get(writeKey, Replicator.ReadLocal))
      probe.expectMsg(Replicator.NotFound(writeKey, None))
      probe.send(replicator, Replicator.Get(deltaKey, Replicator.ReadLocal))
      probe.expectMsg(Replicator.NotFound(deltaKey, None))
    }
  }

  "Replicator" should {

    val events: Seq[MemberEvent] = Seq(
      MemberLeft(memberOn(12345).copy(MemberStatus.Leaving)),
      MemberExited(memberOn(12346).copy(MemberStatus.Leaving).copy(MemberStatus.Exiting)),
      MemberDowned(memberOn(12347).copy(MemberStatus.Down)),
      MemberPreparingForShutdown(memberOn(12348).copy(MemberStatus.Up).copy(MemberStatus.PreparingForShutdown)),
      MemberReadyForShutdown(
        memberOn(12349).copy(MemberStatus.Up).copy(MemberStatus.PreparingForShutdown).copy(
          MemberStatus.ReadyForShutdown)))

    events.foreach { event =>
      s"accept Read and Write from a member first observed in ${event.member.status} until removed" in new TestSetup {
        run {
          probe.send(replicator, event)
          writeAndReadFrom(event.member, "known")
          // Recognizing a sender does not add a departing node to the consistency quorum.
          probe.send(replicator, Replicator.GetReplicaCount)
          probe.expectMsg(Replicator.ReplicaCount(1))
          probe.send(replicator, MemberRemoved(event.member.copy(MemberStatus.Removed), event.member.status))
          rejectReplicationFrom(event.member, "removed")
        }
      }

      s"accept deltas from a member first observed in ${event.member.status}" in new TestSetup {
        run {
          probe.send(replicator, event)
          deltaFrom(event.member, "known-delta")
        }
      }
    }

    Seq[MemberEvent](
      MemberJoined(memberOn(12350)),
      MemberWeaklyUp(memberOn(12351).copy(MemberStatus.WeaklyUp)),
      MemberUp(memberOn(12352).copy(MemberStatus.Up))).foreach { event =>
      s"recognize a member in ${event.member.status} with unchanged replica count" in new TestSetup {
        run {
          probe.send(replicator, event)
          writeAndReadFrom(event.member, "known")
          deltaFrom(event.member, "known-delta")
          probe.send(replicator, Replicator.GetReplicaCount)
          probe.expectMsg(Replicator.ReplicaCount(if (event.member.status == MemberStatus.Up) 2 else 1))
        }
      }
    }

    "recognize its own incarnation" in new TestSetup {
      run {
        writeAndReadFrom(cluster.selfMember, "self")
        deltaFrom(cluster.selfMember, "self-delta")
      }
    }

    "reject a different incarnation at the same remote address" in new TestSetup {
      run {
        val previous = memberOn(12353)
        val current = Member(
          UniqueAddress(previous.address, previous.uniqueAddress.longUid + 1),
          previous.roles,
          Version.Zero).copy(MemberStatus.Leaving)
        probe.send(replicator, MemberLeft(current))
        writeAndReadFrom(current, "current-incarnation")
        rejectReplicationFrom(previous, "previous-incarnation")
      }
    }

    "reject a different incarnation at its own address" in new TestSetup {
      run {
        val other = Member(
          UniqueAddress(cluster.selfAddress, cluster.selfUniqueAddress.longUid + 1),
          cluster.selfRoles,
          Version.Zero).copy(MemberStatus.Leaving)
        probe.send(replicator, MemberLeft(other))
        rejectReplicationFrom(other, "other-incarnation")
      }
    }

    "reject unknown members" in new TestSetup {
      run {
        rejectReplicationFrom(memberOn(12354), "unknown-member")
      }
    }

    "reject members without the configured role" in new TestSetup {
      run {
        val member = memberOn(12355)
        val otherRole = Member(member.uniqueAddress, member.roles - "replica", Version.Zero).copy(MemberStatus.Leaving)
        probe.send(replicator, MemberLeft(otherRole))
        rejectReplicationFrom(otherRole, "other-role")
      }
    }
  }
}
