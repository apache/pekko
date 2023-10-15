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
import static org.junit.Assert.assertFalse;

import java.util.concurrent.CompletionStage;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class ActorSystemTest extends JUnitSuite {

  @Rule
  public final PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("ActorSystemTest");

  private ActorSystem system = null;

  @Before
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
}
