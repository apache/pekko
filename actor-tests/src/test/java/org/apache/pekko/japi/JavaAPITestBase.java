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

package org.apache.pekko.japi;

import static org.junit.Assert.*;

import java.util.concurrent.Callable;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.event.NoLogging;
import org.apache.pekko.serialization.JavaSerializer;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class JavaAPITestBase extends JUnitSuite {

  @Test
  public void mustBeAbleToGetNoLogging() {
    LoggingAdapter a = NoLogging.getInstance();
    assertNotNull(a);
  }

  @Test
  public void mustBeAbleToUseCurrentSystem() {
    assertNull(
        JavaSerializer.currentSystem()
            .withValue(
                null,
                new Callable<ExtendedActorSystem>() {
                  public ExtendedActorSystem call() {
                    return JavaSerializer.currentSystem().value();
                  }
                }));
  }
}
