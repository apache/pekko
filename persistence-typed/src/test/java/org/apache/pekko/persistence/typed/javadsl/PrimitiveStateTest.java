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

package org.apache.pekko.persistence.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class PrimitiveStateTest extends JUnitSuite {

  private static final Config config =
      ConfigFactory.parseString(
          "pekko.persistence.journal.plugin = \"pekko.persistence.journal.inmem\" \n"
              + "pekko.persistence.journal.inmem.test-serialization = on \n");

  @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource(config);

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  static class PrimitiveState extends EventSourcedBehavior<Integer, Integer, Integer> {

    private final ActorRef<String> probe;

    PrimitiveState(PersistenceId persistenceId, ActorRef<String> probe) {
      super(persistenceId);
      this.probe = probe;
    }

    @Override
    public Integer emptyState() {
      return 0;
    }

    @Override
    public SignalHandler<Integer> signalHandler() {
      return newSignalHandlerBuilder()
          .onSignal(
              RecoveryCompleted.instance(),
              state -> {
                probe.tell("onRecoveryCompleted:" + state);
              })
          .build();
    }

    @Override
    public CommandHandler<Integer, Integer, Integer> commandHandler() {
      return (state, command) -> {
        if (command < 0) return Effect().stop();
        else return Effect().persist(command);
      };
    }

    @Override
    public EventHandler<Integer, Integer> eventHandler() {
      return (state, event) -> {
        probe.tell("eventHandler:" + state + ":" + event);
        return state + event;
      };
    }
  }

  @Test
  public void handleIntegerState() throws Exception {
    TestProbe<String> probe = testKit.createTestProbe();
    Behavior<Integer> b =
        Behaviors.setup(ctx -> new PrimitiveState(PersistenceId.ofUniqueId("a"), probe.ref()));
    ActorRef<Integer> ref1 = testKit.spawn(b);
    probe.expectMessage("onRecoveryCompleted:0");
    ref1.tell(1);
    probe.expectMessage("eventHandler:0:1");
    ref1.tell(2);
    probe.expectMessage("eventHandler:1:2");

    ref1.tell(-1);
    // wait for ref1 to terminate
    probe.expectTerminated(ref1);
    ActorRef<Integer> ref2 = testKit.spawn(b);
    // eventHandler from reply
    probe.expectMessage("eventHandler:0:1");
    probe.expectMessage("eventHandler:1:2");
    probe.expectMessage("onRecoveryCompleted:3");
    ref2.tell(3);
    probe.expectMessage("eventHandler:3:3");
  }
}
