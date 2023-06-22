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

import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;

public class StatsSampleOneMasterClientMain {

  public static void main(String[] args) {
    // note that client is not a compute node, role not defined
    ActorSystem system = ActorSystem.create("ClusterSystem", ConfigFactory.load("stats2"));
    system.actorOf(Props.create(StatsSampleClient.class, "/user/statsServiceProxy"), "client");
  }
}
