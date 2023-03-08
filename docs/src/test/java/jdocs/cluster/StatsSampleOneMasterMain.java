/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.singleton.ClusterSingletonManager;
import org.apache.pekko.cluster.singleton.ClusterSingletonManagerSettings;
import org.apache.pekko.cluster.singleton.ClusterSingletonProxy;
import org.apache.pekko.cluster.singleton.ClusterSingletonProxySettings;

public class StatsSampleOneMasterMain {

  public static void main(String[] args) {
    if (args.length == 0) {
      startup(new String[] {"7354", "7355", "0"});
      StatsSampleOneMasterClientMain.main(new String[0]);
    } else {
      startup(args);
    }
  }

  public static void startup(String[] ports) {
    for (String port : ports) {
      // Override the configuration of the port
      Config config =
          ConfigFactory.parseString("pekko.remote.classic.netty.tcp.port=" + port)
              .withFallback(ConfigFactory.parseString("pekko.cluster.roles = [compute]"))
              .withFallback(ConfigFactory.load("stats2"));

      ActorSystem system = ActorSystem.create("ClusterSystem", config);

      // #create-singleton-manager
      ClusterSingletonManagerSettings settings =
          ClusterSingletonManagerSettings.create(system).withRole("compute");
      system.actorOf(
          ClusterSingletonManager.props(
              Props.create(StatsService.class), PoisonPill.getInstance(), settings),
          "statsService");
      // #create-singleton-manager

      // #singleton-proxy
      ClusterSingletonProxySettings proxySettings =
          ClusterSingletonProxySettings.create(system).withRole("compute");
      system.actorOf(
          ClusterSingletonProxy.props("/user/statsService", proxySettings), "statsServiceProxy");
      // #singleton-proxy
    }
  }
}
