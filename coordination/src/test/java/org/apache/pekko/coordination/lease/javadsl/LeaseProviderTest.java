/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.coordination.lease.javadsl;

import static org.junit.Assert.assertEquals;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.coordination.lease.scaladsl.LeaseProviderSpec;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LeaseProviderTest {
  @Rule
  public PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("LoggingAdapterTest", LeaseProviderSpec.config());

  private ActorSystem system = null;

  @Before
  public void before() {
    system = actorSystemResource.getSystem();
  }

  @Test
  public void loadLeaseImpl() {
    Lease leaseA = LeaseProvider.get(system).getLease("a", "lease-a", "owner1");

    assertEquals("a", leaseA.getSettings().leaseName());
    assertEquals("owner1", leaseA.getSettings().ownerName());
    assertEquals("value1", leaseA.getSettings().leaseConfig().getString("key1"));

    Lease leaseB = LeaseProvider.get(system).getLease("b", "lease-b", "owner2");

    assertEquals("b", leaseB.getSettings().leaseName());
    assertEquals("owner2", leaseB.getSettings().ownerName());
    assertEquals("value2", leaseB.getSettings().leaseConfig().getString("key2"));
  }
}
