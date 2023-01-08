/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.org.apache.pekko.discovery;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.discovery.Lookup;
import org.apache.pekko.discovery.Discovery;
import org.apache.pekko.discovery.ServiceDiscovery;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class CompileOnlyTest {
  public static void example() {
    // #loading
    ActorSystem as = ActorSystem.create();
    ServiceDiscovery serviceDiscovery = Discovery.get(as).discovery();
    // #loading

    // #basic
    serviceDiscovery.lookup(Lookup.create("pekko.io"), Duration.ofSeconds(1));
    // convenience for a Lookup with only a serviceName
    serviceDiscovery.lookup("pekko.io", Duration.ofSeconds(1));
    // #basic

    // #full
    CompletionStage<ServiceDiscovery.Resolved> lookup =
        serviceDiscovery.lookup(
            Lookup.create("pekko.io").withPortName("remoting").withProtocol("tcp"),
            Duration.ofSeconds(1));
    // #full

    // not-used warning
    lookup.thenAccept(System.out::println);
  }
}
