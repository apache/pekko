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
