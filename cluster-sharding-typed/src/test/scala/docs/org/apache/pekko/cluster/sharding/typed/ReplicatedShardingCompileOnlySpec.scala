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

package docs.org.apache.pekko.cluster.sharding.typed

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.typed.{ ActorSystem, Behavior }
import pekko.cluster.sharding.typed.scaladsl.{ Entity, EntityRef }
import pekko.cluster.sharding.typed.{
  ReplicatedEntity,
  ReplicatedEntityProvider,
  ReplicatedSharding,
  ReplicatedShardingExtension
}
import pekko.persistence.typed.{ ReplicaId, ReplicationId }

@nowarn("msg=never used")
object ReplicatedShardingCompileOnlySpec {

  sealed trait Command

  val system: ActorSystem[_] = ???

  object MyEventSourcedBehavior {
    def apply(replicationId: ReplicationId): Behavior[Command] = ???
  }

  // #bootstrap
  ReplicatedEntityProvider[Command]("MyEntityType", Set(ReplicaId("DC-A"), ReplicaId("DC-B"))) {
    (entityTypeKey, replicaId) =>
      ReplicatedEntity(replicaId,
        Entity(entityTypeKey) { entityContext =>
          // the sharding entity id contains the business entityId, entityType, and replica id
          // which you'll need to create a ReplicatedEventSourcedBehavior
          val replicationId = ReplicationId.fromString(entityContext.entityId)
          MyEventSourcedBehavior(replicationId)
        })
  }
  // #bootstrap

  // #bootstrap-dc
  ReplicatedEntityProvider.perDataCenter("MyEntityType", Set(ReplicaId("DC-A"), ReplicaId("DC-B"))) { replicationId =>
    MyEventSourcedBehavior(replicationId)
  }
  // #bootstrap-dc

  // #bootstrap-role
  val provider = ReplicatedEntityProvider.perRole("MyEntityType", Set(ReplicaId("DC-A"), ReplicaId("DC-B"))) {
    replicationId =>
      MyEventSourcedBehavior(replicationId)
  }
  // #bootstrap-role

  // #sending-messages
  val myReplicatedSharding: ReplicatedSharding[Command] =
    ReplicatedShardingExtension(system).init(provider)

  val entityRefs: Map[ReplicaId, EntityRef[Command]] = myReplicatedSharding.entityRefsFor("myEntityId")
  // #sending-messages
}
