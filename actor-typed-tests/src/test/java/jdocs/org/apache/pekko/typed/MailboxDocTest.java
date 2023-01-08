/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.typed;

import org.apache.pekko.Done;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.MailboxSelector;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class MailboxDocTest extends JUnitSuite {

  @ClassRule
  public static final TestKitJunitResource testKit =
      new TestKitJunitResource(ConfigFactory.load("mailbox-config-sample.conf"));

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  @Test
  public void startSomeActorsWithDifferentMailboxes() {
    TestProbe<Done> testProbe = testKit.createTestProbe();
    Behavior<String> childBehavior = Behaviors.empty();

    Behavior<Void> setup =
        Behaviors.setup(
            context -> {
              // #select-mailbox
              context.spawn(childBehavior, "bounded-mailbox-child", MailboxSelector.bounded(100));

              context.spawn(
                  childBehavior,
                  "from-config-mailbox-child",
                  MailboxSelector.fromConfig("my-app.my-special-mailbox"));
              // #select-mailbox

              testProbe.ref().tell(Done.getInstance());
              return Behaviors.stopped();
            });

    ActorRef<Void> ref = testKit.spawn(setup);
    testProbe.receiveMessage();
  }
}
