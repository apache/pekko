/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.setup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ActorSystemSetupTest {

  static class JavaSetup extends Setup {
    public final String name;

    public JavaSetup(String name) {
      this.name = name;
    }
  }

  @Test
  public void apiMustBeUsableFromJava() {
    final JavaSetup javaSetting = new JavaSetup("Jasmine Rice");
    final Optional<JavaSetup> result =
        ActorSystemSetup.create().withSetup(javaSetting).get(JavaSetup.class);

    assertTrue(result.isPresent());
    assertEquals(javaSetting, result.get());
  }
}
