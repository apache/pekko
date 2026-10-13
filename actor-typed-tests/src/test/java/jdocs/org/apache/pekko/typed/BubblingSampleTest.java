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

import static jdocs.org.apache.pekko.typed.BubblingSample.Boss;
import static jdocs.org.apache.pekko.typed.BubblingSample.Protocol;

import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class BubblingSampleTest {

  @JUnitJupiterTestKit
  public ActorTestKit testKit =
      new JUnitJupiterTestKitBuilder()
          .withCustomConfig(ConfigFactory.parseString("pekko.loglevel = off"))
          .build();

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
