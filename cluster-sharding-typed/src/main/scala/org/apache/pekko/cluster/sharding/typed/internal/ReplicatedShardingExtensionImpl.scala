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

package org.apache.pekko.cluster.sharding.typed.internal

import java.util.concurrent.atomic.AtomicLong
import java.util.{ Map => JMap }

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.cluster.sharding.typed.ReplicatedShardingExtension
import pekko.cluster.sharding.typed.ReplicatedSharding
import pekko.cluster.sharding.typed.ReplicatedEntityProvider
import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import pekko.cluster.sharding.typed.scaladsl.EntityRef
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.persistence.typed.ReplicaId
import org.slf4j.LoggerFactory
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.cluster.ClusterSettings.DataCenter
import pekko.cluster.sharding.typed.ShardingDirectReplication
import pekko.persistence.typed.ReplicationId
import pekko.util.ccompat.JavaConverters._

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class ReplicatedShardingExtensionImpl(system: ActorSystem[?]) extends ReplicatedShardingExtension {

  private val counter = new AtomicLong(0)

  private val logger = LoggerFactory.getLogger(getClass)

  override def init[M](settings: ReplicatedEntityProvider[M]): ReplicatedSharding[M] =
    initInternal(None, settings)

  override def init[M](thisReplica: ReplicaId, settings: ReplicatedEntityProvider[M]): ReplicatedSharding[M] =
    initInternal(Some(thisReplica), settings)

  private def initInternal[M](
      thisReplica: Option[ReplicaId],
      settings: ReplicatedEntityProvider[M]): ReplicatedSharding[M] = {
    require(settings.replicas.nonEmpty, "Replicas must not be empty")
    val sharding = ClusterSharding(system)
    val initializedReplicas = settings.replicas.map {
      case (replicaSettings, typeName) =>
        // start up a sharding instance per replica id
        logger.infoN(
          "Starting Replicated Event Sourcing sharding for replica [{}] (ShardType: [{}], typeName [{}])",
          replicaSettings.replicaId.id,
          replicaSettings.entity.typeKey.name)
        val regionOrProxy = sharding.init(replicaSettings.entity)
        (
          typeName,
          replicaSettings.replicaId,
          replicaSettings.entity.typeKey,
          regionOrProxy,
          replicaSettings.entity.dataCenter)
    }
    if (settings.directReplication) {
      val replicaToRegionOrProxy = initializedReplicas.map {
        case (_, replicaId, _, regionOrProxy, _) => replicaId -> regionOrProxy
      }.toMap
      val typeNameWithoutReplicaId = settings.replicas.head._2
      logger.infoN("Starting Replicated Event Sourcing Direct Replication")
      system.systemActorOf(
        ShardingDirectReplication(typeNameWithoutReplicaId, thisReplica, replicaToRegionOrProxy),
        s"directReplication-${counter.incrementAndGet()}")
    }

    val replicaToTypeKey = initializedReplicas.map {
      case (typeName, id, typeKey, _, dc) => id -> ((typeKey, dc, typeName))
    }.toMap
    new ReplicatedShardingImpl(sharding, replicaToTypeKey)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class ReplicatedShardingImpl[M](
    sharding: ClusterSharding,
    replicaTypeKeys: Map[ReplicaId, (EntityTypeKey[M], Option[DataCenter], String)])
    extends ReplicatedSharding[M] {

  override def entityRefsFor(entityId: String): Map[ReplicaId, EntityRef[M]] =
    replicaTypeKeys.map {
      case (replicaId, (typeKey, dc, typeName)) =>
        replicaId -> (dc match {
          case None => sharding.entityRefFor(typeKey, ReplicationId(typeName, entityId, replicaId).persistenceId.id)
          case Some(dc) =>
            sharding.entityRefFor(typeKey, ReplicationId(typeName, entityId, replicaId).persistenceId.id, dc)
        })
    }

  override def getEntityRefsFor(entityId: String): JMap[ReplicaId, pekko.cluster.sharding.typed.javadsl.EntityRef[M]] =
    entityRefsFor(entityId).transform((_, v) => v.asJava).asJava

}
