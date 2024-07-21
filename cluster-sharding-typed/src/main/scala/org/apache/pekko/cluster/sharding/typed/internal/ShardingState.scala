/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.internal

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._
import pekko.annotation.InternalApi
import pekko.cluster.sharding.ClusterSharding
import pekko.cluster.sharding.ShardRegion
import pekko.cluster.sharding.ShardRegion.CurrentShardRegionState
import pekko.cluster.sharding.typed.ClusterShardingQuery
import pekko.cluster.sharding.typed.GetClusterShardingStats
import pekko.cluster.sharding.typed.GetShardRegionState

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ShardingState {

  def behavior(classicSharding: ClusterSharding): Behavior[ClusterShardingQuery] =
    Behaviors
      .supervise[ClusterShardingQuery] {
        Behaviors.setup { context =>
          Behaviors.receiveMessage {
            case GetShardRegionState(key, replyTo) =>
              if (classicSharding.getShardTypeNames.contains(key.name)) {
                try
                  classicSharding.shardRegion(key.name).tell(ShardRegion.GetShardRegionState, replyTo.toClassic)
                catch {
                  case e: IllegalStateException =>
                    // classicSharding.shardRegion may throw if not initialized
                    context.log.warn(e.getMessage)
                    replyTo ! CurrentShardRegionState(Set.empty)
                }
              } else {
                replyTo ! CurrentShardRegionState(Set.empty)
              }
              Behaviors.same

            case GetClusterShardingStats(key, timeout, replyTo) =>
              if (classicSharding.getShardTypeNames.contains(key.name)) {
                try
                  classicSharding
                    .shardRegion(key.name)
                    .tell(ShardRegion.GetClusterShardingStats(timeout), replyTo.toClassic)
                catch {
                  case e: IllegalStateException =>
                    // classicSharding.shardRegion may throw if not initialized
                    context.log.warn(e.getMessage)
                    replyTo ! ShardRegion.ClusterShardingStats(Map.empty)
                }
              } else {
                replyTo ! ShardRegion.ClusterShardingStats(Map.empty)
              }
              Behaviors.same
          }
        }
      }
      .onFailure(SupervisorStrategy.restart)

}
