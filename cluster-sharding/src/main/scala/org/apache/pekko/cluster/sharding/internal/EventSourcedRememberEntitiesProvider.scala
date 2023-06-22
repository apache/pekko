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

import org.apache.pekko
import pekko.actor.Props
import pekko.annotation.InternalApi
import pekko.cluster.sharding.ClusterShardingSettings
import pekko.cluster.sharding.ShardRegion.ShardId

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class EventSourcedRememberEntitiesProvider(typeName: String, settings: ClusterShardingSettings)
    extends RememberEntitiesProvider {

  // this is backed by an actor using the same events, at the serialization level, as the now removed PersistentShard when state-store-mode=persistence
  // new events can be added but the old events should continue to be handled
  override def shardStoreProps(shardId: ShardId): Props =
    EventSourcedRememberEntitiesShardStore.props(typeName, shardId, settings)

  // Note that this one is never used for the deprecated persistent state store mode, only when state store is ddata
  // combined with eventsourced remember entities storage
  override def coordinatorStoreProps(): Props =
    EventSourcedRememberEntitiesCoordinatorStore.props(typeName, settings)
}
