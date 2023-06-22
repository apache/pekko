/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.external.scaladsl

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.actor.Address
import pekko.annotation.ApiMayChange
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.cluster.sharding.external.ShardLocations

/**
 * API May Change
 *
 * Not for user extension
 */
@ApiMayChange
trait ExternalShardAllocationClient {

  /**
   * Update the given shard's location. The [[Address]] should
   * match one of the nodes in the cluster. If the node has not joined
   * the cluster yet it will be moved to that node after the first cluster
   * sharding rebalance it does.
   *
   * @param shard The shard identifier
   * @param location Location (Pekko node) to allocate the shard to
   * @return Confirmation that the update has been propagated to a majority of cluster nodes
   */
  def updateShardLocation(shard: ShardId, location: Address): Future[Done]

  /**
   * Update all of the provided ShardLocations.
   * The [[Address]] should match one of the nodes in the cluster. If the node has not joined
   * the cluster yet it will be moved to that node after the first cluster
   * sharding rebalance it does.
   *
   * @param locations to update
   * @return Confirmation that the update has been propagates to a majority of cluster nodes
   */
  def updateShardLocations(locations: Map[ShardId, Address]): Future[Done]

  /**
   * Get all the current shard locations that have been set via updateShardLocation
   */
  def shardLocations(): Future[ShardLocations]
}
