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

package org.apache.pekko.actor;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class ActorSelectionTest extends JUnitSuite {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("ActorSelectionTest", PekkoSpec.testConf());

  private final ActorSystem system = actorSystemResource.getSystem();

  @Test
  public void testResolveOne() throws Exception {
    ActorRef actorRef = system.actorOf(Props.create(JavaAPITestActor.class), "ref1");
    ActorSelection selection = system.actorSelection("user/ref1");
    Duration timeout = Duration.ofMillis(10);

    CompletionStage<ActorRef> cs = selection.resolveOne(timeout);

    ActorRef resolvedRef = cs.toCompletableFuture().get(3, TimeUnit.SECONDS);
    assertEquals(actorRef, resolvedRef);
  }
}
