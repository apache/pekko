/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.actor.testkit.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.annotations.JUnit5TestKit;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.scalatestplus.junit.JUnitSuite;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.apache.pekko.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("ActorTestKitTestJUnit5")
@ExtendWith(TestKitJUnit5Extension.class)
@ExtendWith(LogCapturingExtension.class)
class ActorTestKitJUnit5Test extends JUnitSuite {

  @JUnit5TestKit
  public ActorTestKit testKit = new JUnit5TestKitBuilder().build();

  @Test
  void systemNameShouldComeFromTestClassViaJunitResource() {
    assertEquals("ActorTestKitJUnit5Test", testKit.system().name());
  }

  @Test
  void systemNameShouldComeFromTestClass() {
    final ActorTestKit testKit2 = ActorTestKit.create();
    try {
      assertEquals("ActorTestKitJUnit5Test", testKit2.system().name());
    } finally {
      testKit2.shutdownTestKit();
    }
  }

  @Test
  void systemNameShouldComeFromGivenClassName() {
    final ActorTestKit testKit2 = ActorTestKit.create(HashMap.class.getName());
    try {
      // removing package name and such
      assertEquals("HashMap", testKit2.system().name());
    } finally {
      testKit2.shutdownTestKit();
    }
  }

  @Test
  void testKitShouldSpawnActor() throws Exception {
    final CompletableFuture<Done> started = new CompletableFuture<>();
    testKit.spawn(
        Behaviors.setup(
            (context) -> {
              started.complete(done());
              return Behaviors.same();
            }));
    assertNotNull(started.get(3, TimeUnit.SECONDS));
  }
}
