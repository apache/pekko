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

package org.apache.pekko.actor.typed;

import java.time.Duration;

public class SchedulerTest {

  public void compileOnly() {
    // accepts a lambda
    ActorSystem<Void> system = null;
    system
        .scheduler()
        .scheduleAtFixedRate(
            Duration.ofMillis(10),
            Duration.ofMillis(10),
            () -> system.log().info("Woo!"),
            system.executionContext());
    system
        .scheduler()
        .scheduleOnce(
            Duration.ofMillis(10), () -> system.log().info("Woo!"), system.executionContext());
  }
}
