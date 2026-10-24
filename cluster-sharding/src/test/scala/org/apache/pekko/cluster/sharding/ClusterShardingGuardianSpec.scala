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

package org.apache.pekko.cluster.sharding

import org.apache.pekko
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.Identify
import pekko.actor.PoisonPill
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.ddata.Replicator
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors
import pekko.testkit.TestProbe

import com.typesafe.config.ConfigFactory

class ClusterShardingGuardianSpec
    extends PekkoSpec(ConfigFactory.parseString("""
    pekko.actor.provider = cluster
    pekko.remote.artery.canonical.port = 0
    pekko.cluster.sharding.state-store-mode = ddata
  """))
    with ImplicitSender {

  private val cluster = Cluster(system)

  override protected def atStartup(): Unit = {
    cluster.join(cluster.selfAddress)
    awaitAssert(cluster.selfMember.status shouldEqual MemberStatus.Up)
  }

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case i: Int => (i.toString, i)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case i: Int => (math.abs(i) % 10).toString
    case _      => "0"
  }

  private def identifyRef(path: String): ActorRef = {
    val probe = TestProbe()
    system.actorSelection(path).tell(Identify(1), probe.ref)
    probe.expectMsgType[ActorIdentity].ref.get
  }

  "ClusterShardingGuardian" should {

    "not serve a stopped replicator from the per-role cache" in {
      val sharding = ClusterSharding(system)
      sharding.start(
        "type-a",
        TestActors.echoActorProps,
        ClusterShardingSettings(system),
        extractEntityId,
        extractShardId)

      val replicator = identifyRef("/system/sharding/replicator")
      watch(replicator)
      replicator ! PoisonPill
      expectTerminated(replicator)

      // starting another shard type must create a fresh replicator
      // instead of handing out the stopped one from the cache
      sharding.start(
        "type-b",
        TestActors.echoActorProps,
        ClusterShardingSettings(system),
        extractEntityId,
        extractShardId)

      val newReplicator = identifyRef("/system/sharding/replicator")
      newReplicator should not equal (replicator)

      // and it must be alive
      newReplicator ! Replicator.GetKeyIds
      expectMsgType[Replicator.GetKeyIdsResult]
    }
  }
}
