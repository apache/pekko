/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.typed.javadsl;

import org.apache.pekko.Done;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.stream.AbruptStageTerminationException;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.ClassRule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class CustomGuardianAndMaterializerTest extends JUnitSuite {

  @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void useSystemWideMaterialiser() throws Exception {
    CompletionStage<String> result = Source.single("hello").runWith(Sink.head(), testKit.system());

    assertEquals("hello", result.toCompletableFuture().get(3, TimeUnit.SECONDS));
  }

  @Test
  public void createCustomSystemLevelMaterialiser() throws Exception {
    Materializer materializer = Materializer.createMaterializer(testKit.system());

    CompletionStage<String> result = Source.single("hello").runWith(Sink.head(), materializer);

    assertEquals("hello", result.toCompletableFuture().get(3, TimeUnit.SECONDS));
  }

  private static Behavior<String> actorStreamBehavior(ActorRef<Object> probe) {
    return Behaviors.setup(
        (context) -> {
          Materializer materializer = Materializer.createMaterializer(context);

          CompletionStage<Done> done = Source.repeat("hello").runWith(Sink.ignore(), materializer);
          done.whenComplete(
              (success, failure) -> {
                if (success != null) probe.tell(success);
                else probe.tell(failure);
              });

          return Behaviors.receive(String.class)
              .onMessageEquals("stop", () -> Behaviors.stopped())
              .build();
        });
  }

  @Test
  public void createCustomActorLevelMaterializer() throws Exception {
    TestProbe<Object> probe = testKit.createTestProbe();
    ActorRef<String> actor = testKit.spawn(actorStreamBehavior(probe.getRef()));

    actor.tell("stop");

    probe.expectMessageClass(AbruptStageTerminationException.class);
  }
}
