/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor;

import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.concurrent.CompletionStage;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertFalse;

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
