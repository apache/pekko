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

import org.apache.pekko.actor.testkit.typed.TestException;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.RecoveryFailed;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.time.Duration;

import static org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehaviorFailureSpec.conf;

class FailingEventSourcedActor extends EventSourcedBehavior<String, String, String> {

  private final ActorRef<String> probe;
  private final ActorRef<Throwable> recoveryFailureProbe;

  FailingEventSourcedActor(
      PersistenceId persistenceId,
      ActorRef<String> probe,
      ActorRef<Throwable> recoveryFailureProbe) {

    super(
        persistenceId,
        SupervisorStrategy.restartWithBackoff(Duration.ofMillis(1), Duration.ofMillis(5), 0.1));
    this.probe = probe;
    this.recoveryFailureProbe = recoveryFailureProbe;
  }

  @Override
  public SignalHandler<String> signalHandler() {
    return newSignalHandlerBuilder()
        .onSignal(
            RecoveryCompleted.instance(),
            state -> {
              probe.tell("starting");
            })
        .onSignal(
            RecoveryFailed.class,
            (state, signal) -> {
              recoveryFailureProbe.tell(signal.getFailure());
            })
        .build();
  }

  @Override
  public String emptyState() {
    return "";
  }

  @Override
  public CommandHandler<String, String, String> commandHandler() {
    return (state, command) -> {
      probe.tell("persisting");
      return Effect().persist(command);
    };
  }

  @Override
  public EventHandler<String, String> eventHandler() {
    return (state, event) -> {
      probe.tell(event);
      return state + event;
    };
  }
}

public class EventSourcedActorFailureTest extends JUnitSuite {

  public static final Config config = conf().withFallback(ConfigFactory.load());

  @ClassRule public static final TestKitJUnitResource testKit = new TestKitJUnitResource(config);

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  public static Behavior<String> fail(
      PersistenceId pid, ActorRef<String> probe, ActorRef<Throwable> recoveryFailureProbe) {
    return new FailingEventSourcedActor(pid, probe, recoveryFailureProbe);
  }

  public static Behavior<String> fail(PersistenceId pid, ActorRef<String> probe) {
    return fail(pid, probe, testKit.<Throwable>createTestProbe().ref());
  }

  @Test
  public void notifyRecoveryFailure() {
    TestProbe<String> probe = testKit.createTestProbe();
    TestProbe<Throwable> recoveryFailureProbe = testKit.createTestProbe();
    Behavior<String> p1 =
        fail(
            PersistenceId.ofUniqueId("fail-recovery-once"),
            probe.ref(),
            recoveryFailureProbe.ref());
    testKit.spawn(p1);
    recoveryFailureProbe.expectMessageClass(TestException.class);
  }

  @Test
  public void persistEvents() throws Exception {
    TestProbe<String> probe = testKit.createTestProbe();
    Behavior<String> p1 = fail(PersistenceId.ofUniqueId("fail-first-2"), probe.ref());
    ActorRef<String> c = testKit.spawn(p1);
    probe.expectMessage("starting");
    // fail
    c.tell("one");
    probe.expectMessage("persisting");
    probe.expectMessage("one");
    probe.expectMessage("starting");
    // fail
    c.tell("two");
    probe.expectMessage("persisting");
    probe.expectMessage("two");
    probe.expectMessage("starting");
    // work
    c.tell("three");
    probe.expectMessage("persisting");
    probe.expectMessage("three");
    // no starting as this one did not fail
    probe.expectNoMessage();
  }
}
