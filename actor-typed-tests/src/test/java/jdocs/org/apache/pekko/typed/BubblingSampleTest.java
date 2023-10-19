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

package jdocs.org.apache.pekko.typed;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.time.Duration;

import static jdocs.org.apache.pekko.typed.BubblingSample.Boss;
import static jdocs.org.apache.pekko.typed.BubblingSample.Protocol;

public class BubblingSampleTest extends JUnitSuite {

  @ClassRule
  public static final TestKitJunitResource testKit =
      new TestKitJunitResource("pekko.loglevel = off");

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  @Test
  public void testBubblingSample() throws Exception {
    ActorRef<Protocol.Command> boss = testKit.spawn(Boss.create(), "upper-management");
    TestProbe<String> replyProbe = testKit.createTestProbe(String.class);
    boss.tell(new Protocol.Hello("hi 1", replyProbe.getRef()));
    replyProbe.expectMessage("hi 1");
    boss.tell(new Protocol.Fail("ping"));

    // message may be lost when MiddleManagement is stopped, but eventually it will be functional
    // again
    replyProbe.awaitAssert(
        () -> {
          boss.tell(new Protocol.Hello("hi 2", replyProbe.getRef()));
          replyProbe.expectMessage(Duration.ofMillis(200), "hi 2");
          return null;
        });
  }
}
