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

package org.apache.pekko.actor;

import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.TestProbe;

import org.junit.ClassRule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class StashJavaAPI extends JUnitSuite {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("StashJavaAPI", ActorWithBoundedStashSpec.testConf());

  private final ActorSystem system = actorSystemResource.getSystem();

  private void testAStashApi(Props props) {
    ActorRef ref = system.actorOf(props);
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "Hello");
    probe.send(ref, "Hello2");
    probe.send(ref, "Hello12");
    probe.expectMsg(5);
  }

  @Test
  public void mustBeAbleToUseStash() {
    testAStashApi(Props.create(StashJavaAPITestActors.WithStash.class));
  }

  @Test
  public void mustBeAbleToUseUnboundedStash() {
    testAStashApi(Props.create(StashJavaAPITestActors.WithUnboundedStash.class));
  }

  @Test
  public void mustBeAbleToUseUnrestrictedStash() {
    testAStashApi(
        Props.create(StashJavaAPITestActors.WithUnrestrictedStash.class)
            .withMailbox("pekko.actor.mailbox.unbounded-deque-based"));
  }
}
