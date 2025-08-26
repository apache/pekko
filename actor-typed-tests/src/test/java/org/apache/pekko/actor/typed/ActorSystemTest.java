/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed;

import static org.junit.Assert.assertFalse;

import java.time.Duration;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class ActorSystemTest extends JUnitSuite {

  @Test
  public void testGetWhenTerminated() throws Exception {
    final ActorSystem<Void> system =
        ActorSystem.create(Behaviors.empty(), "GetWhenTerminatedSystem");
    system.terminate();
    system.terminateAndAwait(Duration.ofSeconds(2));
  }

  @Test
  public void testGetWhenTerminatedWithoutTermination() {
    final ActorSystem<Void> system =
        ActorSystem.create(Behaviors.empty(), "GetWhenTerminatedWithoutTermination");
    assertFalse(system.getWhenTerminated().toCompletableFuture().isDone());
  }
}
