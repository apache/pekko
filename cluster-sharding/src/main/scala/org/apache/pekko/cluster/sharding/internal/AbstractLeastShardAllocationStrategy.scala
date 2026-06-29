/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.internal

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.annotation.InternalApi
import pekko.cluster.Cluster
import pekko.cluster.ClusterEvent.CurrentClusterState
import pekko.cluster.Member
import pekko.cluster.sharding.ShardCoordinator.ActorSystemDependentAllocationStrategy
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.cluster.sharding.internal.ClusterShardAllocationMixin.RegionEntry
import pekko.cluster.sharding.internal.ClusterShardAllocationMixin.ShardSuitabilityOrdering
import pekko.pattern.after

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object AbstractLeastShardAllocationStrategy {
  // kept for binary compatibility - delegate to ClusterShardAllocationMixin
  type AllocationMap = ClusterShardAllocationMixin.AllocationMap
  val ShardSuitabilityOrdering: Ordering[RegionEntry] = ClusterShardAllocationMixin.ShardSuitabilityOrdering
  type RegionEntry = ClusterShardAllocationMixin.RegionEntry
  val RegionEntry: ClusterShardAllocationMixin.RegionEntry.type = ClusterShardAllocationMixin.RegionEntry
}

/**
 * INTERNAL API: Common logic for the least shard allocation strategy implementations
 */
@InternalApi
private[pekko] abstract class AbstractLeastShardAllocationStrategy
    extends ActorSystemDependentAllocationStrategy
    with ClusterShardAllocationMixin {

  @volatile private var system: ActorSystem = _
  @volatile private var cluster: Cluster = _

  override def start(system: ActorSystem): Unit = {
    this.system = system
    cluster = Cluster(system)
  }

  // protected for testability
  override protected def clusterState: CurrentClusterState = cluster.state
  override protected def selfMember: Member = cluster.selfMember

  override def allocateShard(
      requester: ActorRef,
      shardId: ShardId,
      currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]): Future[ActorRef] = {
    val regionEntries = regionEntriesFor(currentShardAllocations)
    if (regionEntries.isEmpty) {
      // very unlikely to ever happen but possible because of cluster state view not yet updated when collecting
      // region entries, view should be updated after a very short time
      after(50.millis)(allocateShard(requester, shardId, currentShardAllocations))(system)
    } else {
      val (region, _) = mostSuitableRegion(regionEntries)
      Future.successful(region)
    }
  }

  final protected def mostSuitableRegion(
      regionEntries: Iterable[RegionEntry]): (ActorRef, immutable.IndexedSeq[ShardId]) = {
    val mostSuitableEntry = regionEntries.min(ShardSuitabilityOrdering)
    mostSuitableEntry.region -> mostSuitableEntry.shardIds
  }

}
