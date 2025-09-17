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
import pekko.actor.Props
import pekko.annotation.InternalApi
import pekko.cluster.sharding.ShardRegion.{ EntityId, ShardId }

/**
 * INTERNAL API
 *
 * Created once for the shard guardian
 */
@InternalApi
private[pekko] trait RememberEntitiesProvider {

  /**
   * Called once per started shard coordinator to create the remember entities coordinator store.
   *
   * Note that this is not used for the deprecated persistent coordinator which has its own impl for keeping track of
   * remembered shards.
   *
   * @return an actor that handles the protocol defined in [[RememberEntitiesCoordinatorStore]]
   */
  def coordinatorStoreProps(): Props

  /**
   * Called once per started shard to create the remember entities shard store
   * @return an actor that handles the protocol defined in [[RememberEntitiesShardStore]]
   */
  def shardStoreProps(shardId: ShardId): Props
}

/**
 * INTERNAL API
 *
 * Could potentially become an open SPI in the future.
 *
 * Implementations are responsible for each of the methods failing the returned future after a timeout.
 */
@InternalApi
private[pekko] object RememberEntitiesShardStore {
  // SPI protocol for a remember entities shard store
  sealed trait Command

  // Note: the store is not expected to receive and handle new update before it has responded to the previous one
  final case class Update(started: Set[EntityId], stopped: Set[EntityId]) extends Command
  // responses for Update
  final case class UpdateDone(started: Set[EntityId], stopped: Set[EntityId])

  case object GetEntities extends Command
  final case class RememberedEntities(entities: Set[EntityId])

}

/**
 * INTERNAL API
 *
 * Could potentially become an open SPI in the future.
 */
@InternalApi
private[pekko] object RememberEntitiesCoordinatorStore {
  // SPI protocol for a remember entities coordinator store
  sealed trait Command

  /**
   * Sent once for every started shard (but could be retried), should result in a response of either
   * UpdateDone or UpdateFailed
   */
  final case class AddShard(entityId: ShardId) extends Command
  final case class UpdateDone(entityId: ShardId)
  final case class UpdateFailed(entityId: ShardId)

  /**
   * Sent once when the coordinator starts (but could be retried), should result in a response of
   * RememberedShards
   */
  case object GetShards extends Command
  final case class RememberedShards(entities: Set[ShardId])
  // No message for failed load since we eager lod the set of shards, may need to change in the future
}
