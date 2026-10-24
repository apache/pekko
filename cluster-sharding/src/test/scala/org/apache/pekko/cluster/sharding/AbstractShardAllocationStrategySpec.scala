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

import scala.collection.immutable
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors.EchoActor
import pekko.testkit.TestProbe
import pekko.testkit.WithLogCapturing

class AbstractShardAllocationStrategySpec
    extends PekkoSpec("""
      pekko.actor.provider = cluster
      pekko.remote.artery.canonical.port = 0
      pekko.remote.classic.netty.tcp.port = 0
      pekko.cluster.sharding.state-store-mode = ddata
      pekko.cluster.sharding.distributed-data.durable.keys = []
      pekko.cluster.sharding.rebalance-interval = 200 ms
      pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
      pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
      """)
    with WithLogCapturing {

  private val regionA: ActorRef = TestProbe().ref
  private val regionB: ActorRef = TestProbe().ref

  private val allocations: Map[ActorRef, immutable.IndexedSeq[ShardId]] = Map(
    regionA -> immutable.IndexedSeq("shard-1", "shard-2"),
    regionB -> immutable.IndexedSeq("shard-3"))

  private val expectedAllocations: Map[ActorRef, List[ShardId]] =
    allocations.map { case (region, shards) => region -> shards.toList }

  private def seenAllocations(strategy: RecordingShardAllocationStrategy): Map[ActorRef, List[ShardId]] = {
    val seen = strategy.allocationsSeen.asScala
    seen should have size 1
    seen.head.asScala.iterator.map { case (region, shards) => region -> shards.asScala.toList }.toMap
  }

  "The Java API of AbstractShardAllocationStrategy" must {

    "convert allocation values only when the Java strategy accesses them" in {
      val lookupOnlyAllocations = new immutable.AbstractMap[ActorRef, immutable.IndexedSeq[ShardId]] {
        override def get(key: ActorRef): Option[immutable.IndexedSeq[ShardId]] = allocations.get(key)
        override def size: Int = allocations.size
        override def isEmpty: Boolean = allocations.isEmpty
        override def iterator: Iterator[(ActorRef, immutable.IndexedSeq[ShardId])] =
          throw new AssertionError("The allocation map must not be traversed before the strategy requests it")
        override def removed(key: ActorRef): Map[ActorRef, immutable.IndexedSeq[ShardId]] = allocations.removed(key)
        override def updated[V1 >: immutable.IndexedSeq[ShardId]](key: ActorRef, value: V1): Map[ActorRef, V1] =
          allocations.updated(key, value)
      }
      val strategy = new RecordingShardAllocationStrategy

      strategy.allocateShard(regionA, "shard-4", lookupOnlyAllocations).futureValue should ===(regionA)
      strategy.rebalance(lookupOnlyAllocations, Set.empty[ShardId]).futureValue should ===(Set.empty[ShardId])

      strategy.allocationsSeen.asScala.foreach { seen =>
        seen.size() should ===(allocations.size)
        seen.isEmpty should ===(false)
        seen.containsKey(regionA) should ===(true)
        seen.containsKey(testActor) should ===(false)
        seen.containsKey("not an actor") should ===(false)
        seen.get(regionA).asScala.toList should ===(List("shard-1", "shard-2"))
        seen.get(testActor) shouldBe null
        seen.get("not an actor") shouldBe null
        seen.get(null) shouldBe null
      }
    }

    "hand the requester, shard id and current allocations to the Java implementation" in {
      val strategy = new RecordingShardAllocationStrategy
      strategy.allocateResult = regionB

      strategy.allocateShard(regionA, "shard-4", allocations).futureValue should ===(regionB)

      strategy.requestersSeen.asScala.toList should ===(List(regionA))
      strategy.shardIdsSeen.asScala.toList should ===(List("shard-4"))
      // List comparison also asserts that the shards keep the order they were allocated in
      seenAllocations(strategy) should ===(expectedAllocations)
    }

    "return the region that the CompletionStage completed with" in {
      val strategy = new RecordingShardAllocationStrategy
      strategy.allocateResult = regionA

      strategy.allocateShard(regionB, "shard-4", allocations).futureValue should ===(regionA)
    }

    "keep the coordinator allocations and rebalance-in-progress set read-only" in {
      val strategy = new RecordingShardAllocationStrategy
      strategy.rebalance(allocations, Set("shard-2")).futureValue should ===(Set.empty[ShardId])

      val seen = strategy.allocationsSeen.get(0)
      intercept[UnsupportedOperationException] {
        seen.remove(regionA)
      }
      def checkReadOnlyEntry[T](entry: java.util.Map.Entry[ActorRef, T]): Unit = {
        intercept[UnsupportedOperationException] {
          entry.setValue(entry.getValue)
        }
      }
      checkReadOnlyEntry(seen.entrySet().iterator().next())
      val shardIds = seen.get(regionA).iterator()
      shardIds.next() should ===("shard-1")
      intercept[UnsupportedOperationException] {
        shardIds.remove()
      }
      intercept[UnsupportedOperationException] {
        strategy.rebalanceInProgressSeen.get(0).remove("shard-2")
      }
      seenAllocations(strategy) should ===(expectedAllocations)
    }

    "pass an empty allocation map to rebalance" in {
      val strategy = new RecordingShardAllocationStrategy

      strategy.rebalance(Map.empty[ActorRef, immutable.IndexedSeq[ShardId]], Set.empty[ShardId])
        .futureValue should ===(Set.empty[ShardId])
      strategy.allocationsSeen.get(0).isEmpty should ===(true)
    }

    "hand rebalanceInProgress over as a java.util.Set" in {
      val strategy = new RecordingShardAllocationStrategy
      strategy.rebalanceResult = Set("shard-1", "shard-3").asJava

      strategy.rebalance(allocations, Set("shard-2")).futureValue should ===(Set("shard-1", "shard-3"))

      strategy.rebalanceInProgressSeen.asScala.toList.map(_.asScala.toSet) should ===(List(Set("shard-2")))
      seenAllocations(strategy) should ===(expectedAllocations)
    }

    "convert an empty rebalance result into an empty Set" in {
      val strategy = new RecordingShardAllocationStrategy
      strategy.rebalanceResult = Set.empty[String].asJava

      strategy.rebalance(allocations, Set.empty[ShardId]).futureValue should ===(Set.empty[ShardId])
    }

    "propagate a failed CompletionStage as a failed Future" in {
      val strategy = new RecordingShardAllocationStrategy
      strategy.failure = new IllegalStateException("boom")

      val allocate = strategy.allocateShard(regionA, "shard-4", allocations)
      val rebalance = strategy.rebalance(allocations, Set.empty[ShardId])

      (allocate.failed.futureValue should be).theSameInstanceAs(strategy.failure)
      (rebalance.failed.futureValue should be).theSameInstanceAs(strategy.failure)
    }

    "start a Java strategy before allocating and rebalancing through the coordinator" in within(10.seconds) {
      val cluster = Cluster(system)
      cluster.join(cluster.selfAddress)
      awaitAssert(cluster.selfMember.status should ===(MemberStatus.Up))

      val strategy = new RecordingShardAllocationStrategy
      val region = ClusterSharding(system).start(
        typeName = "java-strategy",
        entityProps = Props[EchoActor](),
        settings = ClusterShardingSettings(system),
        extractEntityId = { case id: String => (id, id) },
        extractShardId = {
          case id: String                  => id
          case ShardRegion.StartEntity(id) => id
          case message                     => throw new IllegalArgumentException(s"Unexpected message: $message")
        },
        allocationStrategy = strategy,
        handOffStopMessage = PoisonPill)

      val probe = TestProbe()
      probe.send(region, "shard-1")
      probe.expectMsg("shard-1")
      probe.send(region, "shard-2")
      probe.expectMsg("shard-2")

      awaitAssert {
        val calls = strategy.calls.asScala.toList
        calls.head should ===("start")
        calls.count(_ == "start") should ===(1)
        calls.count(_ == "allocateShard") should ===(2)
        calls should contain("rebalance")
        strategy.requestersSeen.asScala.toList should ===(List(region, region))
        strategy.shardIdsSeen.asScala.toList should ===(List("shard-1", "shard-2"))
        // the coordinator's allocation view must hand the shard ids over as a java.util.List
        strategy.allocationsSeen.asScala.exists { seen =>
          Option(seen.get(region)).exists { shards =>
            shards.size() == 2 && shards.asScala.toList == List("shard-1", "shard-2")
          }
        } should ===(true)
      }

      probe.send(region, ShardRegion.GetShardRegionState)
      probe.expectMsgType[ShardRegion.CurrentShardRegionState].shards.map { shard =>
        shard.shardId -> shard.entityIds
      } should ===(Set("shard-1" -> Set("shard-1"), "shard-2" -> Set("shard-2")))
    }
  }
}
