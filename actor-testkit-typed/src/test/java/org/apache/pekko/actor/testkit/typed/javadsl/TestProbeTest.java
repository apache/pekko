/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed.javadsl;

import static org.junit.Assert.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbeSpec;
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbeSpec.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class TestProbeTest extends JUnitSuite {

  @ClassRule public static TestKitJunitResource testKit = new TestKitJunitResource();

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  @Test
  public void testReceiveMessage() {
    TestProbe<EventT> probe = TestProbe.create(testKit.system());

    List<EventT> eventsT = org.apache.pekko.japi.Util.javaArrayList(TestProbeSpec.eventsT(10));

    eventsT.forEach(
        e -> {
          probe.getRef().tell(e);
          assertEquals(probe.receiveMessage(), e);
        });

    probe.expectNoMessage();
  }

  @Test
  public void testReceiveMessageMaxDuration() {
    TestProbe<EventT> probe = TestProbe.create(testKit.system());

    List<EventT> eventsT = org.apache.pekko.japi.Util.javaArrayList(TestProbeSpec.eventsT(2));

    eventsT.forEach(
        e -> {
          probe.getRef().tell(e);
          assertEquals(probe.receiveMessage(Duration.ofMillis(100)), e);
        });

    probe.expectNoMessage();
  }

  @Test(expected = AssertionError.class)
  public void testReceiveMessageFailOnTimeout() {
    TestProbe<EventT> probe = TestProbe.create(testKit.system());
    probe.receiveMessage(Duration.ofMillis(100));
  }

  @Test
  public void testAwaitAssert() {
    TestProbe<String> probe = TestProbe.create(testKit.system());
    probe.awaitAssert(
        () -> {
          // ... something ...
          return null;
        });
    probe.awaitAssert(
        Duration.ofSeconds(3),
        () -> {
          // ... something ...
          return null;
        });
    String awaitAssertResult =
        probe.awaitAssert(
            Duration.ofSeconds(3),
            Duration.ofMillis(100),
            () -> {
              // ... something ...
              return "some result";
            });
    assertEquals("some result", awaitAssertResult);
  }

  @Test(expected = Exception.class)
  public void testAwaitAssertThrowingCheckedException() {
    TestProbe<String> probe = TestProbe.create(testKit.system());
    probe.awaitAssert(
        () -> {
          throw new Exception("checked exception");
        });
  }

  @Test
  public void testExpectMessage() {
    TestProbe<String> probe = TestProbe.create(testKit.system());
    probe.getRef().tell("message");
    String messageResult = probe.expectMessage("message");
    probe.getRef().tell("message2");
    String expectClassResult = probe.expectMessageClass(String.class);
    probe.expectNoMessage();
  }

  @Test
  public void testFish() {
    TestProbe<String> probe = TestProbe.create(testKit.system());
    probe.getRef().tell("one");
    probe.getRef().tell("one");
    probe.getRef().tell("two");
    List<String> results =
        probe.fishForMessage(
            Duration.ofSeconds(3),
            "hint",
            message -> {
              if (message.equals("one")) return FishingOutcomes.continueAndIgnore();
              else if (message.equals("two")) return FishingOutcomes.complete();
              else return FishingOutcomes.fail("error");
            });
    assertEquals(Arrays.asList("two"), results);
  }

  @Test
  public void testWithin() {
    TestProbe<String> probe = TestProbe.create(testKit.system());
    String withinResult =
        probe.within(
            Duration.ofSeconds(3),
            () -> {
              // ... something ...
              return "result";
            });
    assertEquals("result", withinResult);
  }
}
