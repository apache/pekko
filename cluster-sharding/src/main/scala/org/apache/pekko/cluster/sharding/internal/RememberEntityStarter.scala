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
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.NoSerializationVerificationNeeded
import pekko.actor.Props
import pekko.actor.Timers
import pekko.annotation.InternalApi
import pekko.cluster.sharding.ClusterShardingSettings
import pekko.cluster.sharding.Shard
import pekko.cluster.sharding.ShardRegion
import pekko.cluster.sharding.ShardRegion.EntityId
import pekko.cluster.sharding.ShardRegion.ShardId

import scala.collection.immutable.Set
import scala.concurrent.ExecutionContext

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object RememberEntityStarter {
  def props(
      region: ActorRef,
      shard: ActorRef,
      shardId: ShardRegion.ShardId,
      ids: Set[ShardRegion.EntityId],
      settings: ClusterShardingSettings) =
    Props(new RememberEntityStarter(region, shard, shardId, ids, settings))

  private final case class StartBatch(batchSize: Int) extends NoSerializationVerificationNeeded
  private case object ResendUnAcked extends NoSerializationVerificationNeeded
}

/**
 * INTERNAL API: Actor responsible for starting entities when rememberEntities is enabled
 */
@InternalApi
private[pekko] final class RememberEntityStarter(
    region: ActorRef,
    shard: ActorRef,
    shardId: ShardRegion.ShardId,
    ids: Set[ShardRegion.EntityId],
    settings: ClusterShardingSettings)
    extends Actor
    with ActorLogging
    with Timers {

  implicit val ec: ExecutionContext = context.dispatcher
  import RememberEntityStarter._

  require(ids.nonEmpty)

  private var idsLeftToStart = Set.empty[EntityId]
  private var waitingForAck = Set.empty[EntityId]
  private var entitiesMoved = Set.empty[EntityId]

  log.debug(
    "Shard starting [{}] remembered entities using strategy [{}]",
    ids.size,
    settings.tuningParameters.entityRecoveryStrategy)

  settings.tuningParameters.entityRecoveryStrategy match {
    case "all" =>
      idsLeftToStart = Set.empty
      startBatch(ids)
    case "constant" =>
      import settings.tuningParameters
      idsLeftToStart = ids
      timers.startTimerWithFixedDelay(
        "constant",
        StartBatch(tuningParameters.entityRecoveryConstantRateStrategyNumberOfEntities),
        tuningParameters.entityRecoveryConstantRateStrategyFrequency)
      startBatch(tuningParameters.entityRecoveryConstantRateStrategyNumberOfEntities)
  }
  timers.startTimerWithFixedDelay("retry", ResendUnAcked, settings.tuningParameters.retryInterval)

  override def receive: Receive = {
    case StartBatch(batchSize)                                => startBatch(batchSize)
    case ShardRegion.StartEntityAck(entityId, ackFromShardId) => onAck(entityId, ackFromShardId)
    case ResendUnAcked                                        => retryUnacked()
  }

  private def onAck(entityId: EntityId, ackFromShardId: ShardId): Unit = {
    idsLeftToStart -= entityId
    waitingForAck -= entityId
    if (shardId != ackFromShardId) entitiesMoved += entityId
    if (waitingForAck.isEmpty && idsLeftToStart.isEmpty) {
      if (entitiesMoved.nonEmpty) {
        log.info("Found [{}] entities moved to new shard(s)", entitiesMoved.size)
        shard ! Shard.EntitiesMovedToOtherShard(entitiesMoved)
      }
      context.stop(self)
    }
  }

  private def startBatch(batchSize: Int): Unit = {
    log.debug("Starting batch of [{}] remembered entities", batchSize)
    val (batch, newIdsLeftToStart) = idsLeftToStart.splitAt(batchSize)
    idsLeftToStart = newIdsLeftToStart
    startBatch(batch)
  }

  private def startBatch(entityIds: Set[EntityId]): Unit = {
    // these go through the region rather the directly to the shard
    // so that shard id extractor changes make them start on the right shard
    waitingForAck = waitingForAck.union(entityIds)
    entityIds.foreach(entityId => region ! ShardRegion.StartEntity(entityId))
  }

  private def retryUnacked(): Unit =
    if (waitingForAck.nonEmpty) {
      log.debug("Found [{}] remembered entities waiting for StartEntityAck, retrying", waitingForAck.size)
      waitingForAck.foreach { id =>
        // for now we just retry all (as that was the existing behavior spread out over starter and shard)
        // but in the future it could perhaps make sense to batch also the retries to avoid thundering herd
        region ! ShardRegion.StartEntity(id)
      }
    }

}
