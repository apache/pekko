/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.actor.testkit.typed.javadsl;

import static jdocs.org.apache.pekko.actor.testkit.typed.javadsl.AsyncTestingExampleTest.Echo;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.junit.Rule;

// #junit-integration
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

public class JunitIntegrationExampleTest {

  @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

  // #junit-integration
  // this is shown in LogCapturingExampleTest
  @Rule public final LogCapturing logCapturing = new LogCapturing();
  // #junit-integration

  @Test
  public void testSomething() {
    ActorRef<Echo.Ping> pinger = testKit.spawn(Echo.create(), "ping");
    TestProbe<Echo.Pong> probe = testKit.createTestProbe();
    pinger.tell(new Echo.Ping("hello", probe.ref()));
    probe.expectMessage(new Echo.Pong("hello"));
  }
}
// #junit-integration
