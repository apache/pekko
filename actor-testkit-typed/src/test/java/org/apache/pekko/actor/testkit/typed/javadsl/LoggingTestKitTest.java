/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.LoggingEvent;
import org.apache.pekko.actor.testkit.typed.TestException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoggingTestKitTest extends JUnitSuite {

  @ClassRule public static TestKitJunitResource testKit = new TestKitJunitResource();

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  private LoggingEvent errorNoCause() {
    return LoggingEvent.create(
        Level.ERROR,
        getClass().getName(),
        Thread.currentThread().getName(),
        "this is an error",
        System.currentTimeMillis(),
        Optional.empty(),
        Optional.empty(),
        Collections.emptyMap());
  }

  private LoggingEvent errorWithCause(Throwable cause) {
    return LoggingEvent.create(
        Level.ERROR,
        getClass().getName(),
        Thread.currentThread().getName(),
        "this is an error",
        System.currentTimeMillis(),
        Optional.empty(),
        Optional.of(cause),
        Collections.emptyMap());
  }

  @Test
  public void filterErrorsWithMatchingMessage() {
    assertTrue(LoggingTestKit.error("an error").matches(errorWithCause(new TestException("exc"))));
    assertTrue(LoggingTestKit.error("an error").matches(errorNoCause()));
    assertFalse(LoggingTestKit.error("another error").matches(errorNoCause()));
  }

  @Test
  public void filterErrorsWithMatchingCause() {
    assertTrue(
        LoggingTestKit.error(TestException.class)
            .matches(errorWithCause(new TestException("exc"))));
    assertFalse(
        LoggingTestKit.error(TestException.class)
            .matches(errorWithCause(new RuntimeException("exc"))));
    assertTrue(
        LoggingTestKit.error("an error")
            .withCause(TestException.class)
            .matches(errorWithCause(new TestException("exc"))));
    assertFalse(
        LoggingTestKit.error("another error")
            .withCause(TestException.class)
            .matches(errorWithCause(new TestException("exc"))));
  }

  @Test
  public void filterErrorsWithMatchingCustomFunction() {
    assertTrue(LoggingTestKit.custom(event -> true).matches(errorNoCause()));
    assertFalse(
        LoggingTestKit.custom(event -> event.getMdc().containsKey("aKey")).matches(errorNoCause()));
  }
}
