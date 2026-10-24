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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.cluster.sharding.ShardCoordinator.AbstractShardAllocationStrategy;
import org.apache.pekko.cluster.sharding.ShardCoordinator.StartableAllocationStrategy;

/**
 * A custom shard allocation strategy written in Java. It only uses Java types, both for the values
 * it is handed and for the values it returns. Every call records its arguments so that tests can
 * assert on what the Scala API of {@link AbstractShardAllocationStrategy} forwards to the Java API.
 *
 * <p>It deliberately never traverses the allocation map it is handed, so that a test can tell apart
 * a lazily wrapped map from one that Pekko already copied.
 */
public class RecordingShardAllocationStrategy extends AbstractShardAllocationStrategy
    implements StartableAllocationStrategy {

  public final List<Map<ActorRef, ? extends List<String>>> allocationsSeen =
      new CopyOnWriteArrayList<>();
  public final List<Set<String>> rebalanceInProgressSeen = new CopyOnWriteArrayList<>();
  public final List<ActorRef> requestersSeen = new CopyOnWriteArrayList<>();
  public final List<String> shardIdsSeen = new CopyOnWriteArrayList<>();
  public final List<String> calls = new CopyOnWriteArrayList<>();

  // Read on the coordinator's thread, written from the test thread.
  public volatile ActorRef allocateResult;
  public volatile Set<String> rebalanceResult = new HashSet<>();
  public volatile RuntimeException failure;

  @Override
  public void start() {
    calls.add("start");
  }

  @Override
  public CompletionStage<ActorRef> allocateShard(
      ActorRef requester,
      String shardId,
      Map<ActorRef, ? extends List<String>> currentShardAllocations) {
    calls.add("allocateShard");
    requestersSeen.add(requester);
    shardIdsSeen.add(shardId);
    allocationsSeen.add(currentShardAllocations);
    if (failure != null) {
      return failed(failure);
    }
    return CompletableFuture.completedFuture(allocateResult == null ? requester : allocateResult);
  }

  @Override
  public CompletionStage<Set<String>> rebalance(
      Map<ActorRef, ? extends List<String>> currentShardAllocations,
      Set<String> rebalanceInProgress) {
    calls.add("rebalance");
    allocationsSeen.add(currentShardAllocations);
    rebalanceInProgressSeen.add(rebalanceInProgress);
    if (failure != null) {
      return failed(failure);
    }
    return CompletableFuture.completedFuture(rebalanceResult);
  }

  private static <T> CompletionStage<T> failed(Throwable cause) {
    CompletableFuture<T> stage = new CompletableFuture<>();
    stage.completeExceptionally(cause);
    return stage;
  }
}
