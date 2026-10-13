/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.cluster.sharding.ConsistentHashingShardAllocationStrategy
import pekko.cluster.sharding.typed.ShardingEnvelope
import pekko.cluster.sharding.typed.ShardingMessageExtractor
import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey

class ConsistentHashingShardAllocationCompileOnlySpec {

  // #building
  object Building {
    val TypeKey = EntityTypeKey[Command]("Building")

    val NumberOfShards = 100

    final class MessageExtractor extends ShardingMessageExtractor[ShardingEnvelope[Command], Command] {

      override def entityId(envelope: ShardingEnvelope[Command]): String =
        envelope.entityId

      override def shardId(entityId: String): String =
        math.abs(entityId.hashCode % NumberOfShards).toString

      override def unwrapMessage(envelope: ShardingEnvelope[Command]): Command =
        envelope.message
    }

    sealed trait Command

    def apply(entityId: String): Behavior[Command] = ???
  }

  // #building

  // #device
  object Device {
    val TypeKey = EntityTypeKey[Command]("Device")

    final class MessageExtractor extends ShardingMessageExtractor[ShardingEnvelope[Command], Command] {

      override def entityId(envelope: ShardingEnvelope[Command]): String =
        envelope.entityId

      override def shardId(entityId: String): String = {
        // Use same shardId as the Building to colocate Building and Device
        // we have the buildingId as prefix in the entityId
        val buildingId = entityId.split(':').head
        math.abs(buildingId.hashCode % Building.NumberOfShards).toString
      }

      override def unwrapMessage(envelope: ShardingEnvelope[Command]): Command =
        envelope.message
    }

    sealed trait Command

    def apply(entityId: String): Behavior[Command] = ???
  }

  // #device

  val system: ActorSystem[?] = ???

  // #init
  ClusterSharding(system).init(
    Entity(Building.TypeKey)(createBehavior = entityContext => Building(entityContext.entityId))
      .withMessageExtractor(new Building.MessageExtractor)
      .withAllocationStrategy(new ConsistentHashingShardAllocationStrategy(rebalanceLimit = 10)))

  ClusterSharding(system).init(
    Entity(Device.TypeKey)(createBehavior = entityContext => Device(entityContext.entityId))
      .withMessageExtractor(new Device.MessageExtractor)
      .withAllocationStrategy(new ConsistentHashingShardAllocationStrategy(rebalanceLimit = 10)))
  // #init

}
