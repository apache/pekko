/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */


package jdocs.org.apache.pekko.actor.testkit.typed.javadsl;

// #log-capturing-junit5

import org.apache.pekko.actor.testkit.typed.javadsl.*;
import org.apache.pekko.actor.testkit.typed.javadsl.Junit5TestKitBuilder;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static jdocs.org.apache.pekko.actor.testkit.typed.javadsl.AsyncTestingExampleTest.Echo;

// test code copied from LogCapturingExampleTest.java

@DisplayName("Junit5 log capturing")
@ExtendWith(TestKitJunit5Extension.class)
@ExtendWith(LogCapturingExtension.class)
class LogCapturingExtensionExampleTest {

  @Junit5TestKit public ActorTestKit testKit = new Junit5TestKitBuilder().build();

  @Test
  void testSomething() {
    ActorRef<Echo.Ping> pinger = testKit.spawn(Echo.create(), "ping");
    TestProbe<Echo.Pong> probe = testKit.createTestProbe();
    pinger.tell(new Echo.Ping("hello", probe.ref()));
    probe.expectMessage(new Echo.Pong("hello"));
  }

  @Test
  void testSomething2() {
    ActorRef<Echo.Ping> pinger = testKit.spawn(Echo.create(), "ping");
    TestProbe<Echo.Pong> probe = testKit.createTestProbe();
    pinger.tell(new Echo.Ping("hello", probe.ref()));
    probe.expectMessage(new Echo.Pong("hello"));
  }
}
// #log-capturing-junit5
