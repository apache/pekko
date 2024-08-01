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

package org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.cluster.sharding.typed.javadsl.{ Entity => JEntity, EntityTypeKey => JEntityTypeKey }
import pekko.persistence.typed.ReplicaId

import scala.collection.immutable
import scala.reflect.ClassTag
import pekko.util.ccompat.JavaConverters._
import java.util.{ Set => JSet }

import pekko.actor.typed.Behavior
import pekko.cluster.sharding.typed.internal.EntityTypeKeyImpl
import pekko.persistence.typed.ReplicationId
import pekko.persistence.typed.ReplicationId.Separator

object ReplicatedEntityProvider {

  /**
   * Java API:
   *
   * Provides full control over the [[ReplicatedEntity]] and the [[javadsl.Entity]]
   * Most use cases can use the [[createPerDataCenter]] and [[createPerRole]]
   *
   * @tparam M The type of messages the replicated entity accepts
   */
  def create[M](
      messageClass: Class[M],
      typeName: String,
      allReplicaIds: JSet[ReplicaId],
      settingsPerReplicaFactory: pekko.japi.function.Function2[JEntityTypeKey[M], ReplicaId, ReplicatedEntity[M]])
      : ReplicatedEntityProvider[M] = {
    implicit val classTag: ClassTag[M] = ClassTag(messageClass)
    apply[M](typeName, allReplicaIds.asScala.toSet)((key, replica) =>
      settingsPerReplicaFactory(key.asInstanceOf[EntityTypeKeyImpl[M]], replica))
  }

  /**
   * Scala API:
   *
   * Provides full control over the [[ReplicatedEntity]] and the [[scaladsl.Entity]]
   * Most use cases can use the [[perDataCenter]] and [[perRole]]
   *
   * @param typeName The type name used in the [[scaladsl.EntityTypeKey]]
   * @tparam M The type of messages the replicated entity accepts
   */
  def apply[M: ClassTag](typeName: String, allReplicaIds: Set[ReplicaId])(
      settingsPerReplicaFactory: (EntityTypeKey[M], ReplicaId) => ReplicatedEntity[M]): ReplicatedEntityProvider[M] =
    new ReplicatedEntityProvider(
      allReplicaIds.map { replicaId =>
        if (typeName.contains(Separator))
          throw new IllegalArgumentException(
            s"typeName [$typeName] contains [$Separator] which is a reserved character")

        val typeKey = EntityTypeKey[M](s"$typeName$Separator${replicaId.id}")
        (settingsPerReplicaFactory(typeKey, replicaId), typeName)
      }.toVector, directReplication = true)

  /**
   * Scala API
   *
   * Create a [[ReplicatedEntityProvider]] that uses the defaults for [[scaladsl.Entity]] when running in
   * ClusterSharding. A replica will be run per data center.
   */
  def perDataCenter[M: ClassTag, E](typeName: String, allReplicaIds: Set[ReplicaId])(
      create: ReplicationId => Behavior[M]): ReplicatedEntityProvider[M] =
    apply(typeName, allReplicaIds) { (typeKey, replicaId) =>
      ReplicatedEntity(replicaId,
        Entity(typeKey) { entityContext =>
          create(ReplicationId.fromString(entityContext.entityId))
        }.withDataCenter(replicaId.id))
    }

  /**
   * Scala API
   *
   * Create a [[ReplicatedEntityProvider]] that uses the defaults for [[scaladsl.Entity]] when running in
   * ClusterSharding. The replicas in allReplicaIds should be roles used by nodes. A replica for each
   * entity will run on each role.
   */
  def perRole[M: ClassTag, E](typeName: String, allReplicaIds: Set[ReplicaId])(
      create: ReplicationId => Behavior[M]): ReplicatedEntityProvider[M] =
    apply(typeName, allReplicaIds) { (typeKey, replicaId) =>
      ReplicatedEntity(replicaId,
        Entity(typeKey) { entityContext =>
          create(ReplicationId.fromString(entityContext.entityId))
        }.withRole(replicaId.id))
    }

  /**
   * Java API
   *
   * Create a [[ReplicatedEntityProvider]] that uses the defaults for [[scaladsl.Entity]] when running in
   * ClusterSharding. A replica will be run per data center.
   */
  def createPerDataCenter[M](
      messageClass: Class[M],
      typeName: String,
      allReplicaIds: JSet[ReplicaId],
      createBehavior: java.util.function.Function[ReplicationId, Behavior[M]]): ReplicatedEntityProvider[M] = {
    implicit val classTag: ClassTag[M] = ClassTag(messageClass)
    apply(typeName, allReplicaIds.asScala.toSet) { (typeKey, replicaId) =>
      ReplicatedEntity(replicaId,
        Entity(typeKey) { entityContext =>
          createBehavior(ReplicationId.fromString(entityContext.entityId))
        }.withDataCenter(replicaId.id))
    }
  }

  /**
   * Java API
   *
   * Create a [[ReplicatedEntityProvider]] that uses the defaults for [[scaladsl.Entity]] when running in
   * ClusterSharding.
   *
   * Map replicas to roles and then there will be a replica per role e.g. to match to availability zones/racks
   */
  def createPerRole[M](
      messageClass: Class[M],
      typeName: String,
      allReplicaIds: JSet[ReplicaId],
      createBehavior: pekko.japi.function.Function[ReplicationId, Behavior[M]]): ReplicatedEntityProvider[M] = {
    implicit val classTag: ClassTag[M] = ClassTag(messageClass)
    apply(typeName, allReplicaIds.asScala.toSet) { (typeKey, replicaId) =>
      ReplicatedEntity(replicaId,
        Entity(typeKey) { entityContext =>
          createBehavior(ReplicationId.fromString(entityContext.entityId))
        }.withRole(replicaId.id))
    }
  }
}

/**
 * @tparam M The type of messages the replicated entity accepts
 */
final class ReplicatedEntityProvider[M] private (
    val replicas: immutable.Seq[(ReplicatedEntity[M], String)],
    val directReplication: Boolean) {

  /**
   * Start direct replication over sharding when replicated sharding starts up, requires the entities
   * to also have it enabled through [[pekko.persistence.typed.scaladsl.EventSourcedBehavior.withEventPublishing]]
   * or [[pekko.persistence.typed.javadsl.ReplicatedEventSourcedBehavior.withEventPublishing]]
   * to work.
   */
  def withDirectReplication(enabled: Boolean): ReplicatedEntityProvider[M] =
    new ReplicatedEntityProvider(replicas, directReplication = enabled)

}

object ReplicatedEntity {

  /**
   * Java API: Defines the [[pekko.cluster.sharding.typed.javadsl.Entity]] to use for a given replica, note that the behavior
   * can be a [[pekko.persistence.typed.javadsl.ReplicatedEventSourcedBehavior]] or an arbitrary non persistent
   * [[pekko.actor.typed.Behavior]] but must never be a regular [[pekko.persistence.typed.javadsl.EventSourcedBehavior]]
   * as that requires a single writer and that would cause it to have multiple writers.
   */
  def create[M](replicaId: ReplicaId, entity: JEntity[M, ShardingEnvelope[M]]): ReplicatedEntity[M] =
    apply(replicaId, entity.toScala)

  /**
   * Scala API: Defines the [[pekko.cluster.sharding.typed.scaladsl.Entity]] to use for a given replica, note that the behavior
   * can be a behavior created with [[pekko.persistence.typed.scaladsl.ReplicatedEventSourcing]] or an arbitrary non persistent
   * [[pekko.actor.typed.Behavior]] but must never be a regular [[pekko.persistence.typed.scaladsl.EventSourcedBehavior]]
   * as that requires a single writer and that would cause it to have multiple writers.
   */
  def apply[M](replicaId: ReplicaId, entity: Entity[M, ShardingEnvelope[M]]): ReplicatedEntity[M] =
    new ReplicatedEntity(replicaId, entity)
}

/**
 * Settings for a specific replica id in replicated sharding
 * Currently only Entity's with ShardingEnvelope are supported but this may change in the future
 */
final class ReplicatedEntity[M] private (val replicaId: ReplicaId, val entity: Entity[M, ShardingEnvelope[M]])
