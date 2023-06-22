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

import org.apache.pekko.Done;
import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.external.ExternalShardAllocation;
import org.apache.pekko.cluster.sharding.external.javadsl.ExternalShardAllocationClient;
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

import java.util.concurrent.CompletionStage;

import static jdocs.org.apache.pekko.cluster.sharding.typed.ShardingCompileOnlyTest.Counter;

public class ExternalShardAllocationCompileOnlyTest {

  void example() {
    ActorSystem<?> system = null;

    ClusterSharding sharding = ClusterSharding.get(system);

    // #entity
    EntityTypeKey<Counter.Command> typeKey = EntityTypeKey.create(Counter.Command.class, "Counter");

    ActorRef<ShardingEnvelope<Counter.Command>> shardRegion =
        sharding.init(Entity.of(typeKey, ctx -> Counter.create(ctx.getEntityId())));
    // #entity

    // #client
    ExternalShardAllocationClient client =
        ExternalShardAllocation.get(system).getClient(typeKey.name());
    CompletionStage<Done> done =
        client.setShardLocation("shard-id-1", new Address("pekko", "system", "127.0.0.1", 7355));
    // #client

  }
}
