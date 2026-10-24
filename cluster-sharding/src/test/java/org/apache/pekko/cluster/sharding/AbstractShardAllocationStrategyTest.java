/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.cluster.sharding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AbstractShardAllocationStrategyTest {
  @RegisterExtension
  static final PekkoJUnitJupiterActorSystemResource actorSystem =
      new PekkoJUnitJupiterActorSystemResource("AbstractShardAllocationStrategyTest");

  private final ActorRef region = actorSystem.getSystem().deadLetters();

  @Test
  void acceptArrayListValues() {
    RecordingShardAllocationStrategy strategy = new RecordingShardAllocationStrategy();
    Map<ActorRef, ArrayList<String>> allocations =
        Map.of(region, new ArrayList<>(List.of("shard-1", "shard-2")));

    assertSame(
        region,
        strategy.allocateShard(region, "shard-3", allocations).toCompletableFuture().join());
    assertEquals(Set.of(), strategy.rebalance(allocations, Set.of()).toCompletableFuture().join());
    assertEquals(List.of(allocations, allocations), strategy.allocationsSeen);
  }

  @Test
  void acceptLinkedListValues() {
    RecordingShardAllocationStrategy strategy = new RecordingShardAllocationStrategy();
    Map<ActorRef, LinkedList<String>> allocations =
        Map.of(region, new LinkedList<>(List.of("shard-1", "shard-2")));

    assertSame(
        region,
        strategy.allocateShard(region, "shard-3", allocations).toCompletableFuture().join());
    assertEquals(Set.of(), strategy.rebalance(allocations, Set.of()).toCompletableFuture().join());
    assertEquals(List.of(allocations, allocations), strategy.allocationsSeen);
  }

  @Test
  void exposeAllocationValuesAsLists() {
    RecordingShardAllocationStrategy strategy = new RecordingShardAllocationStrategy();
    Map<ActorRef, List<String>> allocations = Map.of(region, List.of("shard-1", "shard-2"));

    assertSame(
        region,
        strategy.allocateShard(region, "shard-3", allocations).toCompletableFuture().join());

    // A least-shards strategy counts the shards of every region; that must not need a downcast.
    Map<ActorRef, ? extends List<String>> seen = strategy.allocationsSeen.get(0);
    List<String> shardsOfRegion = seen.get(region);
    assertEquals(2, shardsOfRegion.size());
    assertEquals("shard-1", shardsOfRegion.get(0));
    assertEquals(1, seen.size());
    assertFalse(shardsOfRegion.isEmpty());
    int totalShards = 0;
    for (Map.Entry<ActorRef, ? extends List<String>> entry : seen.entrySet()) {
      totalShards += entry.getValue().size();
    }
    assertEquals(2, totalShards);
  }
}
