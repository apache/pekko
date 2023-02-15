/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import org.apache.pekko
import pekko.actor.Address
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.event.LogMarker

/**
 * This is public with the purpose to document the used markers and properties of log events.
 * No guarantee that it will remain binary compatible, but the marker names and properties
 * are considered public API and will not be changed without notice.
 */
@ApiMayChange
object ShardingLogMarker {

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] object Properties {
    val ShardTypeName = "akkaShardTypeName"
    val ShardId = "akkaShardId"
  }

  /**
   * Marker "akkaShardAllocated" of log event when `ShardCoordinator` allocates a shard to a region.
   * @param shardTypeName The `typeName` of the shard. Included as property "akkaShardTypeName".
   * @param shardId The id of the shard. Included as property "akkaShardId".
   * @param node The address of the node where the shard is allocated. Included as property "pekkoRemoteAddress".
   */
  def shardAllocated(shardTypeName: String, shardId: String, node: Address): LogMarker =
    LogMarker(
      "akkaShardAllocated",
      Map(
        Properties.ShardTypeName -> shardTypeName,
        Properties.ShardId -> shardId,
        LogMarker.Properties.RemoteAddress -> node))

  /**
   * Marker "akkaShardStarted" of log event when `ShardRegion` starts a shard.
   * @param shardTypeName The `typeName` of the shard. Included as property "akkaShardTypeName".
   * @param shardId The id of the shard. Included as property "akkaShardId".
   */
  def shardStarted(shardTypeName: String, shardId: String): LogMarker =
    LogMarker("akkaShardStarted", Map(Properties.ShardTypeName -> shardTypeName, Properties.ShardId -> shardId))

}
