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

import com.typesafe.config.ConfigFactory;
import org.apache.pekko.Done;
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Dispatchers;
import org.apache.pekko.actor.typed.MailboxSelector;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class MailboxDocTest {

  @JUnitJupiterTestKit
  public ActorTestKit testKit =
      new JUnitJupiterTestKitBuilder()
          .withCustomConfig(ConfigFactory.load("mailbox-config-sample.conf"))
          .build();

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

  @Test
  public void startSomeActorsWithMailboxSelectorInteroperability() {
    TestProbe<Done> testProbe = testKit.createTestProbe();
    Behavior<String> childBehavior = Behaviors.empty();

    Behavior<Void> setup =
        Behaviors.setup(
            context -> {
              // #interoperability-with-dispatcher
              context.spawn(
                  childBehavior,
                  "bounded-mailbox-child",
                  MailboxSelector.bounded(100).withDispatcherDefault());

              context.spawn(
                  childBehavior,
                  "from-config-mailbox-child",
                  MailboxSelector.fromConfig("my-app.my-special-mailbox")
                      .withDispatcherFromConfig(Dispatchers.DefaultDispatcherId()));
              // #interoperability-with-dispatcher

              testProbe.ref().tell(Done.getInstance());
              return Behaviors.stopped();
            });

    ActorRef<Void> ref = testKit.spawn(setup);
    testProbe.receiveMessage();
  }
}
