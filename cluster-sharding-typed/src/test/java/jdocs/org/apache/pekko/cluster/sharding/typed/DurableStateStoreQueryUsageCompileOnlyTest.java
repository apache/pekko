/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.cluster.sharding.typed;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.stream.javadsl.Source;

// #get-durable-state-store-query-example
import org.apache.pekko.persistence.state.DurableStateStoreRegistry;
import org.apache.pekko.persistence.query.javadsl.DurableStateStoreQuery;
import org.apache.pekko.persistence.query.DurableStateChange;
import org.apache.pekko.persistence.query.UpdatedDurableState;

// #get-durable-state-store-query-example

class DurableStateStoreQueryUsageCompileOnlySpec {

  @SuppressWarnings("unchecked")
  public <Record> DurableStateStoreQuery<Record> getQuery(
      ActorSystem system, String pluginId, Offset offset) {
    // #get-durable-state-store-query-example
    DurableStateStoreQuery<Record> durableStateStoreQuery =
        DurableStateStoreRegistry.get(system)
            .getDurableStateStoreFor(DurableStateStoreQuery.class, pluginId);
    Source<DurableStateChange<Record>, NotUsed> source =
        durableStateStoreQuery.changes("tag", offset);
    source.map(
        chg -> {
          if (chg instanceof UpdatedDurableState) {
            UpdatedDurableState<Record> upd = (UpdatedDurableState<Record>) chg;
            return upd.value();
          } else {
            throw new IllegalArgumentException("Unexpected DurableStateChange " + chg.getClass());
          }
        });
    // #get-durable-state-store-query-example
    return durableStateStoreQuery;
  }
}
