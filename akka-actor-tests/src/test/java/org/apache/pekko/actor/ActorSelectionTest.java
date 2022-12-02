/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;

import org.junit.ClassRule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import static org.junit.Assert.assertEquals;

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
