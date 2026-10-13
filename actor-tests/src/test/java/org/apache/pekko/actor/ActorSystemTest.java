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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletionStage;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ActorSystemTest {

  @RegisterExtension
  PekkoJUnitJupiterActorSystemResource actorSystemResource =
      new PekkoJUnitJupiterActorSystemResource("ActorSystemTest");

  private ActorSystem system = null;

  @BeforeEach
  public void beforeEach() {
    system = actorSystemResource.getSystem();
  }

  @Test
  public void testGetWhenTerminated() throws Exception {
    system.terminate();
    final CompletionStage<Terminated> cs = system.getWhenTerminated();
    cs.toCompletableFuture().get(2, SECONDS);
  }

  @Test
  public void testGetWhenTerminatedWithoutTermination() {
    assertFalse(system.getWhenTerminated().toCompletableFuture().isDone());
  }

  @Test
  public void testTryWithResources() throws Exception {
    ActorSystem system = null;
    try (ActorSystem actorSystem = ActorSystem.create()) {
      system = actorSystem;
    }
    final CompletionStage<Terminated> cs = system.getWhenTerminated();
    assertTrue(cs.toCompletableFuture().isDone());
  }
}
