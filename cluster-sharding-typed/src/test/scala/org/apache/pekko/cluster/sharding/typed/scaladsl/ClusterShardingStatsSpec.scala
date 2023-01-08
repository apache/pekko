/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.scaladsl

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.cluster.sharding.ShardRegion.ClusterShardingStats
import pekko.cluster.sharding.ShardRegion.ShardRegionStats
import pekko.cluster.sharding.typed.ClusterShardingSettings
import pekko.cluster.sharding.typed.GetClusterShardingStats
import pekko.cluster.sharding.typed.scaladsl.ClusterShardingSpec._
import pekko.cluster.typed.Cluster
import pekko.cluster.typed.Join
import pekko.cluster.typed.SelfUp

class ClusterShardingStatsSpec
    extends ScalaTestWithActorTestKit(ClusterShardingSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  private val sharding = ClusterSharding(system)

  private val typeKey: EntityTypeKey[IdTestProtocol] = ClusterShardingSpec.typeKeyWithoutEnvelopes

  private val shardExtractor = ClusterShardingSpec.idTestProtocolMessageExtractor

  // no need to scale this up here for the cluster query versus one region
  private val queryTimeout = ClusterShardingSettings(system).shardRegionQueryTimeout

  "Cluster Sharding ClusterShardingStats query" must {
    "return empty statistics if there are no running sharded entities" in {
      val cluster = Cluster(system)
      val upProbe = createTestProbe[SelfUp]()

      cluster.subscriptions ! pekko.cluster.typed.Subscribe(upProbe.ref, classOf[SelfUp])
      cluster.manager ! Join(cluster.selfMember.address)
      upProbe.expectMessageType[SelfUp]

      val replyProbe = createTestProbe[ClusterShardingStats]()
      sharding.shardState ! GetClusterShardingStats(typeKey, queryTimeout, replyProbe.ref)
      replyProbe.expectMessage(ClusterShardingStats(Map.empty))
    }

    "allow querying of statistics of the currently running sharded entities in the entire cluster" in {
      val shardingRef: ActorRef[IdTestProtocol] = sharding.init(
        Entity(typeKey)(_ => ClusterShardingSpec.behaviorWithId())
          .withStopMessage(IdStopPlz())
          .withMessageExtractor(idTestProtocolMessageExtractor))

      val replyProbe = createTestProbe[String]()
      val id1 = "id1"
      shardingRef ! IdReplyPlz(id1, replyProbe.ref)
      replyProbe.expectMessage("Hello!")

      val replyToProbe = createTestProbe[ClusterShardingStats]()
      val replyTo = replyToProbe.ref

      ClusterSharding(system).shardState ! GetClusterShardingStats(typeKey, queryTimeout, replyTo)
      val stats = replyToProbe.receiveMessage()

      val expect = ClusterShardingStats(
        Map(Cluster(system).selfMember.address -> ShardRegionStats(Map(shardExtractor.shardId(id1) -> 1), Set.empty)))

      stats shouldEqual expect
    }

  }

}
