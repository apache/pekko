/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.singleton;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;

public class ClusterSingletonManagerTest {

  @SuppressWarnings("null")
  public void demo() {
    final ActorSystem system = null;
    final ActorRef queue = null;
    final ActorRef testActor = null;

    // #create-singleton-manager
    final ClusterSingletonManagerSettings settings =
        ClusterSingletonManagerSettings.create(system).withRole("worker");

    system.actorOf(
        ClusterSingletonManager.props(
            Props.create(Consumer.class, () -> new Consumer(queue, testActor)),
            TestSingletonMessages.end(),
            settings),
        "consumer");
    // #create-singleton-manager

    // #create-singleton-proxy
    ClusterSingletonProxySettings proxySettings =
        ClusterSingletonProxySettings.create(system).withRole("worker");

    ActorRef proxy =
        system.actorOf(
            ClusterSingletonProxy.props("/user/consumer", proxySettings), "consumerProxy");
    // #create-singleton-proxy

    // #create-singleton-proxy-dc
    ActorRef proxyDcB =
        system.actorOf(
            ClusterSingletonProxy.props(
                "/user/consumer",
                ClusterSingletonProxySettings.create(system)
                    .withRole("worker")
                    .withDataCenter("B")),
            "consumerProxyDcB");
    // #create-singleton-proxy-dc
  }
}
