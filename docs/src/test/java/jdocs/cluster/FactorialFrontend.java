/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.time.Duration;

import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.metrics.AdaptiveLoadBalancingGroup;
import org.apache.pekko.cluster.metrics.AdaptiveLoadBalancingPool;
import org.apache.pekko.cluster.metrics.HeapMetricsSelector;
import org.apache.pekko.cluster.metrics.SystemLoadAverageMetricsSelector;
import org.apache.pekko.cluster.routing.ClusterRouterGroup;
import org.apache.pekko.cluster.routing.ClusterRouterGroupSettings;
import org.apache.pekko.cluster.routing.ClusterRouterPool;
import org.apache.pekko.cluster.routing.ClusterRouterPoolSettings;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ReceiveTimeout;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.routing.FromConfig;

// #frontend
public class FactorialFrontend extends AbstractActor {
  final int upToN;
  final boolean repeat;

  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  ActorRef backend =
      getContext().actorOf(FromConfig.getInstance().props(), "factorialBackendRouter");

  public FactorialFrontend(int upToN, boolean repeat) {
    this.upToN = upToN;
    this.repeat = repeat;
  }

  @Override
  public void preStart() {
    sendJobs();
    getContext().setReceiveTimeout(Duration.ofSeconds(10));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            FactorialResult.class,
            result -> {
              if (result.n == upToN) {
                log.debug("{}! = {}", result.n, result.factorial);
                if (repeat) sendJobs();
                else getContext().stop(getSelf());
              }
            })
        .match(
            ReceiveTimeout.class,
            x -> {
              log.info("Timeout");
              sendJobs();
            })
        .build();
  }

  void sendJobs() {
    log.info("Starting batch of factorials up to [{}]", upToN);
    for (int n = 1; n <= upToN; n++) {
      backend.tell(n, getSelf());
    }
  }
}
// #frontend

// not used, only for documentation
abstract class FactorialFrontend2 extends AbstractActor {
  // #router-lookup-in-code
  int totalInstances = 100;
  Iterable<String> routeesPaths = Arrays.asList("/user/factorialBackend", "");
  boolean allowLocalRoutees = true;
  Set<String> useRoles = new HashSet<>(Arrays.asList("backend"));
  ActorRef backend =
      getContext()
          .actorOf(
              new ClusterRouterGroup(
                      new AdaptiveLoadBalancingGroup(
                          HeapMetricsSelector.getInstance(), Collections.<String>emptyList()),
                      new ClusterRouterGroupSettings(
                          totalInstances, routeesPaths, allowLocalRoutees, useRoles))
                  .props(),
              "factorialBackendRouter2");

  // #router-lookup-in-code
}

// not used, only for documentation
abstract class FactorialFrontend3 extends AbstractActor {
  // #router-deploy-in-code
  int totalInstances = 100;
  int maxInstancesPerNode = 3;
  boolean allowLocalRoutees = false;
  Set<String> useRoles = new HashSet<>(Arrays.asList("backend"));
  ActorRef backend =
      getContext()
          .actorOf(
              new ClusterRouterPool(
                      new AdaptiveLoadBalancingPool(
                          SystemLoadAverageMetricsSelector.getInstance(), 0),
                      new ClusterRouterPoolSettings(
                          totalInstances, maxInstancesPerNode, allowLocalRoutees, useRoles))
                  .props(Props.create(FactorialBackend.class)),
              "factorialBackendRouter3");
  // #router-deploy-in-code
}
