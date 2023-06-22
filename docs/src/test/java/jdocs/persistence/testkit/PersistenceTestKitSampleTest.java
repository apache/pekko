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

package jdocs.persistence.testkit;

import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin;
import org.apache.pekko.persistence.testkit.javadsl.PersistenceTestKit;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;
import org.apache.pekko.serialization.jackson.CborSerializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.typesafe.config.ConfigFactory;
import jdocs.AbstractJavaTest;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

// #test
public class PersistenceTestKitSampleTest extends AbstractJavaTest {

  @ClassRule
  public static final TestKitJunitResource testKit =
      new TestKitJunitResource(
          PersistenceTestKitPlugin.getInstance()
              .config()
              .withFallback(ConfigFactory.defaultApplication()));

  PersistenceTestKit persistenceTestKit = PersistenceTestKit.create(testKit.system());

  @Before
  public void beforeEach() {
    persistenceTestKit.clearAll();
  }

  @Test
  public void test() {
    PersistenceId persistenceId = PersistenceId.ofUniqueId("some-id");
    ActorRef<YourPersistentBehavior.Cmd> ref =
        testKit.spawn(YourPersistentBehavior.create(persistenceId));

    YourPersistentBehavior.Cmd cmd = new YourPersistentBehavior.Cmd("data");
    ref.tell(cmd);
    YourPersistentBehavior.Evt expectedEventPersisted = new YourPersistentBehavior.Evt(cmd.data);

    persistenceTestKit.expectNextPersisted(persistenceId.id(), expectedEventPersisted);
  }
}

class YourPersistentBehavior
    extends EventSourcedBehavior<
        YourPersistentBehavior.Cmd, YourPersistentBehavior.Evt, YourPersistentBehavior.State> {

  static final class Cmd implements CborSerializable {

    public final String data;

    @JsonCreator
    public Cmd(String data) {
      this.data = data;
    }
  }

  static final class Evt implements CborSerializable {

    public final String data;

    @JsonCreator
    public Evt(String data) {
      this.data = data;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Evt evt = (Evt) o;

      return data.equals(evt.data);
    }

    @Override
    public int hashCode() {
      return data.hashCode();
    }
  }

  static final class State implements CborSerializable {}

  static Behavior<Cmd> create(PersistenceId persistenceId) {
    return Behaviors.setup(context -> new YourPersistentBehavior(persistenceId));
  }

  private YourPersistentBehavior(PersistenceId persistenceId) {
    super(persistenceId);
  }

  @Override
  public State emptyState() {
    // some state
    return new State();
  }

  @Override
  public CommandHandler<Cmd, Evt, State> commandHandler() {
    return newCommandHandlerBuilder()
        .forAnyState()
        .onCommand(Cmd.class, command -> Effect().persist(new Evt(command.data)))
        .build();
  }

  @Override
  public EventHandler<State, Evt> eventHandler() {
    // TODO handle events
    return newEventHandlerBuilder().forAnyState().onEvent(Evt.class, (state, evt) -> state).build();
  }
}
// #test
