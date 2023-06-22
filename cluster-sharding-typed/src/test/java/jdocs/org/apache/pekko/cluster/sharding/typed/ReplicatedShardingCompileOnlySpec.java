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

package jdocs.org.apache.pekko.cluster.sharding.typed;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.sharding.typed.*;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.persistence.typed.ReplicaId;
import org.apache.pekko.persistence.typed.ReplicationId;

import java.util.*;

public class ReplicatedShardingCompileOnlySpec {

  private static ActorSystem<?> system = null;

  interface Command {}

  public static Behavior<Command> myEventSourcedBehavior(ReplicationId replicationId) {
    return null;
  }

  public static final Set<ReplicaId> ALL_REPLICAS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(new ReplicaId("DC-A"), new ReplicaId("DC-B"), new ReplicaId("DC-C"))));

  public static ReplicatedEntityProvider<Command> provider() {
    // #bootstrap
    return ReplicatedEntityProvider.create(
        Command.class,
        "MyReplicatedType",
        ALL_REPLICAS,
        (entityTypeKey, replicaId) ->
            ReplicatedEntity.create(
                replicaId,
                Entity.of(
                    entityTypeKey,
                    entityContext ->
                        myEventSourcedBehavior(
                            ReplicationId.fromString(entityContext.getEntityId())))));

    // #bootstrap
  }

  public static void dc() {
    // #bootstrap-dc
    ReplicatedEntityProvider.create(
        Command.class,
        "MyReplicatedType",
        ALL_REPLICAS,
        (entityTypeKey, replicaId) ->
            ReplicatedEntity.create(
                replicaId,
                Entity.of(
                        entityTypeKey,
                        entityContext ->
                            myEventSourcedBehavior(
                                ReplicationId.fromString(entityContext.getEntityId())))
                    .withDataCenter(replicaId.id())));

    // #bootstrap-dc
  }

  public static ReplicatedEntityProvider<Command> role() {
    // #bootstrap-role
    return ReplicatedEntityProvider.create(
        Command.class,
        "MyReplicatedType",
        ALL_REPLICAS,
        (entityTypeKey, replicaId) ->
            ReplicatedEntity.create(
                replicaId,
                Entity.of(
                        entityTypeKey,
                        entityContext ->
                            myEventSourcedBehavior(
                                ReplicationId.fromString(entityContext.getEntityId())))
                    .withRole(replicaId.id())));

    // #bootstrap-role
  }

  public static void sendingMessages() {
    // #sending-messages
    ReplicatedShardingExtension extension = ReplicatedShardingExtension.get(system);

    ReplicatedSharding<Command> replicatedSharding = extension.init(provider());

    Map<ReplicaId, EntityRef<Command>> myEntityId =
        replicatedSharding.getEntityRefsFor("myEntityId");
    // #sending-messages

  }
}
