/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.internal

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.annotation.InternalApi
import pekko.cluster.sharding.ClusterShardingSettings
import pekko.cluster.sharding.ShardRegion.ShardId

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class DDataRememberEntitiesProvider(
    typeName: String,
    settings: ClusterShardingSettings,
    majorityMinCap: Int,
    replicator: ActorRef)
    extends RememberEntitiesProvider {

  override def coordinatorStoreProps(): Props =
    DDataRememberEntitiesCoordinatorStore.props(typeName, settings, replicator, majorityMinCap)

  override def shardStoreProps(shardId: ShardId): Props =
    DDataRememberEntitiesShardStore.props(shardId, typeName, settings, replicator, majorityMinCap)
}
