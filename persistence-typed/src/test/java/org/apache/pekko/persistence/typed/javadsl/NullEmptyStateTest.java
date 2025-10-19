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

package org.apache.pekko.persistence.typed.javadsl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class NullEmptyStateTest extends JUnitSuite {

  private static final Config config =
      ConfigFactory.parseString(
          "pekko.persistence.journal.plugin = \"pekko.persistence.journal.inmem\" \n"
              + "pekko.persistence.journal.inmem.test-serialization = on \n");

  @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource(config);

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  static class NullEmptyState extends EventSourcedBehavior<String, String, String> {

    private final ActorRef<String> probe;

    NullEmptyState(PersistenceId persistenceId, ActorRef<String> probe) {
      super(persistenceId);
      this.probe = probe;
    }

    @Override
    public String emptyState() {
      return null;
    }

    @Override
    public SignalHandler<String> signalHandler() {
      return newSignalHandlerBuilder()
          .onSignal(
              RecoveryCompleted.instance(),
              state -> {
                probe.tell("onRecoveryCompleted:" + state);
              })
          .build();
    }

    @Override
    public CommandHandler<String, String, String> commandHandler() {

      return newCommandHandlerBuilder()
          .forAnyState()
          .onCommand("stop"::equals, command -> Effect().stop())
          .onCommand(String.class, this::persistCommand)
          .build();
    }

    private Effect<String, String> persistCommand(String command) {
      return Effect().persist(command);
    }

    @Override
    public EventHandler<String, String> eventHandler() {
      return newEventHandlerBuilder().forAnyState().onEvent(String.class, this::applyEvent).build();
    }

    private String applyEvent(String state, String event) {
      probe.tell("eventHandler:" + state + ":" + event);
      if (state == null) return event;
      else return state + event;
    }
  }

  @Test
  public void handleNullState() throws Exception {
    TestProbe<String> probe = testKit.createTestProbe();
    Behavior<String> b =
        Behaviors.setup(ctx -> new NullEmptyState(PersistenceId.ofUniqueId("a"), probe.ref()));

    ActorRef<String> ref1 = testKit.spawn(b);
    probe.expectMessage("onRecoveryCompleted:null");
    ref1.tell("stop");
    // wait till ref1 stops
    probe.expectTerminated(ref1);

    ActorRef<String> ref2 = testKit.spawn(b);
    probe.expectMessage("onRecoveryCompleted:null");
    ref2.tell("one");
    probe.expectMessage("eventHandler:null:one");
    ref2.tell("two");
    probe.expectMessage("eventHandler:one:two");

    ref2.tell("stop");
    // wait till ref2 stops
    probe.expectTerminated(ref2);
    ActorRef<String> ref3 = testKit.spawn(b);
    // eventHandler from reply
    probe.expectMessage("eventHandler:null:one");
    probe.expectMessage("eventHandler:one:two");
    probe.expectMessage("onRecoveryCompleted:onetwo");
    ref3.tell("three");
    probe.expectMessage("eventHandler:onetwo:three");
  }
}
