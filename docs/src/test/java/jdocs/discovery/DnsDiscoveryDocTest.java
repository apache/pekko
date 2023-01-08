/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.discovery;

import org.apache.pekko.actor.ActorSystem;
// #lookup-dns
import org.apache.pekko.discovery.Discovery;
import org.apache.pekko.discovery.ServiceDiscovery;
// #lookup-dns
import org.apache.pekko.testkit.javadsl.TestKit;
import docs.discovery.DnsDiscoveryDocSpec;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class DnsDiscoveryDocTest extends JUnitSuite {

  private static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("DnsDiscoveryDocTest", DnsDiscoveryDocSpec.config());
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void dnsDiscoveryShouldResolveAkkaIo() throws Exception {
    try {
      // #lookup-dns

      ServiceDiscovery discovery = Discovery.get(system).discovery();
      // ...
      CompletionStage<ServiceDiscovery.Resolved> result =
          discovery.lookup("foo", Duration.ofSeconds(3));
      // #lookup-dns

      result.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      system.log().warning("Failed lookup pekko.io, but ignoring: " + e);
      // don't fail this test
    }
  }
}
