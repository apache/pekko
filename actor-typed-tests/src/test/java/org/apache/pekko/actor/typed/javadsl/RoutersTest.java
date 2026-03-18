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

package org.apache.pekko.actor.typed.javadsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class RoutersTest {

  @JUnitJupiterTestKit
  public ActorTestKit testKit =
      new JUnitJupiterTestKitBuilder().withCustomConfig(PekkoSpec.testConf()).build();

  public void compileOnlyApiTest() {

    final ServiceKey<String> key = ServiceKey.create(String.class, "key");
    Behavior<String> group = Routers.group(key).withRandomRouting().withRoundRobinRouting();

    Behavior<String> pool =
        Routers.pool(5, Behaviors.<String>empty()).withRandomRouting().withRoundRobinRouting();
  }

  @Test
  public void poolBroadcastTest() {
    TestProbe<String> probe = testKit.createTestProbe();
    Behavior<String> behavior =
        Behaviors.receiveMessage(
            (String str) -> {
              probe.getRef().tell(str);
              return Behaviors.same();
            });

    Behavior<String> poolBehavior =
        Routers.pool(4, behavior).withBroadcastPredicate(str -> str.startsWith("bc-"));

    ActorRef<String> pool = testKit.spawn(poolBehavior);

    String notBroadcastMsg = "not-bc-message";
    pool.tell(notBroadcastMsg);

    String broadcastMsg = "bc-message";
    pool.tell(broadcastMsg);

    List<String> messages = probe.receiveSeveralMessages(5);
    assertTrue(messages.contains(notBroadcastMsg), "non-broadcast message arrives");

    int broadcast = 0;
    for (String msg : messages) if (msg == broadcastMsg) broadcast++;
    assertEquals(4, broadcast, "broadcast message arrives 4 times");
  }
}
