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
import pekko.cluster.ClusterEvent.MemberExited
import pekko.cluster.ClusterEvent.MemberLeft
import pekko.cluster.ClusterSettings
import pekko.cluster.Member
import pekko.cluster.MemberStatus
import pekko.cluster.UniqueAddress
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe
import pekko.util.Version

import com.typesafe.config.ConfigFactory

class ReplicatorKnownNodeSpec
    extends PekkoSpec(ConfigFactory.parseString("""
      pekko.actor.provider = cluster
      pekko.remote.artery.canonical.port = 0
    """))
    with ImplicitSender {

  import Replicator.Internal

  private val cluster = Cluster(system)

  override protected def atStartup(): Unit = {
    cluster.join(cluster.selfAddress)
    awaitAssert(cluster.selfMember.status shouldEqual MemberStatus.Up)
  }

  private lazy val replicator = {
    val ref = system.actorOf(Replicator.props(ReplicatorSettings(system)), "replicator1")
    // wait until the initial loading has completed
    val probe = TestProbe()
    probe.send(ref, Replicator.GetReplicaCount)
    probe.expectMsgType[Replicator.ReplicaCount]
    ref
  }

  private def memberOn(port: Int): Member =
    Member(
      UniqueAddress(Address("pekko", system.name, "127.0.0.1", port), port.toLong),
      Set(ClusterSettings.DcRolePrefix + cluster.settings.SelfDataCenter),
      Version.Zero)

  private def writeFrom(member: Member, keyId: String): Unit = {
    val key = GCounterKey(keyId)
    replicator ! Internal.Write(
      key.id,
      Internal.DataEnvelope(GCounter.empty.increment(member.uniqueAddress, 1)),
      Some(member.uniqueAddress))
    expectMsg(Internal.WriteAck)
  }

  "Replicator" should {

    "accept Write from a member first observed in Leaving status" in {
      val member = memberOn(12345).copy(MemberStatus.Leaving)
      replicator ! MemberLeft(member)
      writeFrom(member, "leavingKey")
    }

    "accept Write from a member first observed in Exiting status" in {
      val member = memberOn(12346).copy(MemberStatus.Leaving).copy(MemberStatus.Exiting)
      replicator ! MemberExited(member)
      writeFrom(member, "exitingKey")
    }

    "accept Write from a member first observed in Down status" in {
      val member = memberOn(12347).copy(MemberStatus.Down)
      replicator ! MemberDowned(member)
      writeFrom(member, "downKey")
    }
  }
}
