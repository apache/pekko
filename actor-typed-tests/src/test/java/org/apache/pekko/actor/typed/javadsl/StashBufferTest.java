/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.internal.StubbedActorContext;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.typed.internal.StashBufferImpl;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StashBufferTest extends JUnitSuite {

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  StubbedActorContext<String> context =
      new StubbedActorContext<String>(
          "StashBufferTest",
          () -> {
            throw new UnsupportedOperationException("Will never be invoked in this test");
          });

  @Test
  public void testProcessElementsInTheRightOrder() {

    StashBuffer<String> buffer = StashBufferImpl.apply(context, 10);
    buffer.stash("m1");
    buffer.stash("m2");
    buffer.stash("m3");

    StringBuilder sb1 = new StringBuilder();
    buffer.forEach(sb1::append);
    assertEquals("m1m2m3", sb1.toString());

    buffer.unstash(Behaviors.ignore(), 1, Function.identity());
    StringBuilder sb2 = new StringBuilder();
    buffer.forEach(sb2::append);
    assertEquals("m2m3", sb2.toString());
  }

  @Test
  public void testAnyMatchAndContains() {
    StashBuffer<String> buffer = StashBufferImpl.apply(context, 10);
    buffer.stash("m1");
    buffer.stash("m2");

    assertTrue(buffer.anyMatch(m -> m.startsWith("m")));
    assertTrue(buffer.anyMatch(m -> m.endsWith("2")));

    assertTrue(buffer.contains("m1"));
    assertTrue(buffer.contains("m2"));
  }
}
