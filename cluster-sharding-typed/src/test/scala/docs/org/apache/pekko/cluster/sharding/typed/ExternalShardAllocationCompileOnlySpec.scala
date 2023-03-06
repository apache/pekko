/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.Done
import pekko.actor.Address
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.cluster.sharding.external.ExternalShardAllocation
import pekko.cluster.sharding.external.ExternalShardAllocationStrategy
import pekko.cluster.sharding.external.scaladsl.ExternalShardAllocationClient
import pekko.cluster.sharding.typed.ShardingEnvelope
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import pekko.cluster.sharding.typed.scaladsl.Entity
import docs.org.apache.pekko.cluster.sharding.typed.ShardingCompileOnlySpec.Basics.Counter

import scala.concurrent.Future

class ExternalShardAllocationCompileOnlySpec {
  val system: ActorSystem[_] = ???

  val sharding = ClusterSharding(system)

  // #entity
  val TypeKey = EntityTypeKey[Counter.Command]("Counter")

  val entity = Entity(TypeKey)(createBehavior = entityContext => Counter(entityContext.entityId))
    .withAllocationStrategy(new ExternalShardAllocationStrategy(system, TypeKey.name))
  // #entity

  val shardRegion: ActorRef[ShardingEnvelope[Counter.Command]] =
    sharding.init(entity)

  // #client
  val client: ExternalShardAllocationClient = ExternalShardAllocation(system).clientFor(TypeKey.name)
  val done: Future[Done] = client.updateShardLocation("shard-id-1", Address("pekko", "system", "127.0.0.1", 7355))
  // #client

}
