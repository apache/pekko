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

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.event.NoLogging;
import org.apache.pekko.serialization.JavaSerializer;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.concurrent.Callable;

import static org.junit.Assert.*;

public class JavaAPITestBase extends JUnitSuite {

  @Test
  public void shouldCreateSomeString() {
    Option<String> o = Option.some("abc");
    assertFalse(o.isEmpty());
    assertTrue(o.isDefined());
    assertEquals("abc", o.get());
    assertEquals("abc", o.getOrElse("other"));
  }

  @Test
  public void shouldCreateNone() {
    Option<String> o1 = Option.none();
    assertTrue(o1.isEmpty());
    assertFalse(o1.isDefined());
    assertEquals("other", o1.getOrElse("other"));

    Option<Float> o2 = Option.none();
    assertTrue(o2.isEmpty());
    assertFalse(o2.isDefined());
    assertEquals("other", o1.getOrElse("other"));
  }

  @Test
  public void shouldEnterForLoop() {
    for (@SuppressWarnings("unused") String s : Option.some("abc")) {
      return;
    }
    org.junit.Assert.fail("for-loop not entered");
  }

  @Test
  public void shouldNotEnterForLoop() {
    for (@SuppressWarnings("unused") Object o : Option.none()) {
      org.junit.Assert.fail("for-loop entered");
    }
  }

  @Test
  public void shouldBeSingleton() {
    assertSame(Option.none(), Option.none());
  }

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
