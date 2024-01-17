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
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Extension
import pekko.actor.typed.ExtensionId
import pekko.annotation.DoNotInherit
import pekko.cluster.sharding.typed.internal.ReplicatedShardingExtensionImpl
import pekko.cluster.sharding.typed.scaladsl.EntityRef
import pekko.persistence.typed.ReplicaId
import java.util.{ Map => JMap }

/**
 * Extension for running Replicated Event Sourcing in sharding by starting one separate instance of sharding per replica.
 * The sharding instances can be confined to datacenters or cluster roles or run on the same set of cluster nodes.
 */
object ReplicatedShardingExtension extends ExtensionId[ReplicatedShardingExtension] {

  override def createExtension(system: ActorSystem[?]): ReplicatedShardingExtension =
    new ReplicatedShardingExtensionImpl(system)

  def get(system: ActorSystem[?]): ReplicatedShardingExtension = apply(system)

}

/**
 * Not for user extension.
 */
@DoNotInherit
trait ReplicatedShardingExtension extends Extension {

  /**
   * Init one instance sharding per replica in the given settings and return a [[ReplicatedSharding]] representing those.
   *
   * @tparam M The type of messages the replicated event sourced actor accepts
   *
   * Note, multiple calls on the same node will not start new sharding instances but will return a new instance of [[ReplicatedSharding]]
   */
  def init[M](settings: ReplicatedEntityProvider[M]): ReplicatedSharding[M]

  /**
   * Init one instance sharding per replica in the given settings and return a [[ReplicatedSharding]] representing those.
   *
   * @param thisReplica If provided saves messages being forwarded to sharding for this replica
   * @tparam M The type of messages the replicated event sourced actor accepts
   *
   * Note, multiple calls on the same node will not start new sharding instances but will return a new instance of [[ReplicatedSharding]]
   */
  def init[M](thisReplica: ReplicaId, settings: ReplicatedEntityProvider[M]): ReplicatedSharding[M]
}

/**
 * Represents the sharding instances for the replicas of one Replicated Event Sourcing entity type
 *
 * Not for user extension.
 */
@DoNotInherit
trait ReplicatedSharding[M] {

  /**
   * Scala API: Returns the entity ref for each replica for user defined routing/replica selection
   */
  def entityRefsFor(entityId: String): Map[ReplicaId, EntityRef[M]]

  /**
   * Java API: Returns the entity ref for each replica for user defined routing/replica selection
   */
  def getEntityRefsFor(entityId: String): JMap[ReplicaId, javadsl.EntityRef[M]]
}
