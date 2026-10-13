/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence.testkit.javadsl;

import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.state.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.state.javadsl.DurableStateBehavior;
import org.apache.pekko.serialization.jackson.CborSerializable;

public final class DurableStateBehaviorTestKitApiTest {

  interface Command extends CborSerializable {}

  enum Increment implements Command {
    INSTANCE
  }

  static final class IncrementWithReply implements Command {
    final ActorRef<Done> replyTo;

    @JsonCreator
    IncrementWithReply(@JsonProperty("replyTo") ActorRef<Done> replyTo) {
      this.replyTo = replyTo;
    }
  }

  static final class State implements CborSerializable {
    final int value;

    @JsonCreator
    State(@JsonProperty("value") int value) {
      this.value = value;
    }
  }

  static final class Counter extends DurableStateBehavior<Command, State> {

    static Behavior<Command> create(PersistenceId persistenceId) {
      return new Counter(persistenceId);
    }

    private Counter(PersistenceId persistenceId) {
      super(persistenceId);
    }

    @Override
    public State emptyState() {
      return new State(0);
    }

    @Override
    public CommandHandler<Command, State> commandHandler() {
      return newCommandHandlerBuilder()
          .forAnyState()
          .onCommand(
              Increment.class, (state, command) -> Effect().persist(new State(state.value + 1)))
          .onCommand(
              IncrementWithReply.class,
              (state, command) ->
                  Effect()
                      .persist(new State(state.value + 1))
                      .thenReply(command.replyTo, ignored -> Done.getInstance()))
          .build();
    }
  }

  private static final AtomicInteger idCounter = new AtomicInteger();

  // #basic-test
  public static void run(ActorSystem<?> system) {
    PersistenceId persistenceId =
        PersistenceId.ofUniqueId("durable-state-java-test-" + idCounter.incrementAndGet());
    DurableStateBehaviorTestKit<Command, State> testKit =
        DurableStateBehaviorTestKit.create(system, Counter.create(persistenceId));

    DurableStateBehaviorTestKit.CommandResult<Command, State> commandResult =
        testKit.runCommand(Increment.INSTANCE);
    check(commandResult.command() == Increment.INSTANCE, "command");
    check(commandResult.state().value == 1, "first command state");
    check(commandResult.stateOfType(State.class).value == 1, "typed state");

    DurableStateBehaviorTestKit.CommandResultWithReply<Command, State, Done> replyResult =
        testKit.runCommand(replyTo -> new IncrementWithReply(replyTo));
    check(replyResult.state().value == 2, "reply command state");
    check(replyResult.reply() == Done.getInstance(), "reply");
    check(replyResult.replyOfType(Done.class) == Done.getInstance(), "typed reply");
    check(!replyResult.hasNoReply(), "has reply");

    check(testKit.getState().value == 2, "current state");
    check(testKit.restart().state().value == 2, "recovered state");
  }

  // #basic-test

  public static void clear(ActorSystem<?> system) {
    PersistenceId persistenceId =
        PersistenceId.ofUniqueId("durable-state-java-clear-" + idCounter.incrementAndGet());
    DurableStateBehaviorTestKit<Command, State> testKit =
        DurableStateBehaviorTestKit.create(
            system,
            Counter.create(persistenceId),
            DurableStateBehaviorTestKit.enabledSerializationSettings());

    testKit.runCommand(Increment.INSTANCE);
    testKit.clear();
    check(testKit.getState().value == 0, "cleared state");
  }

  private static void check(boolean condition, String clue) {
    if (!condition) {
      throw new AssertionError(clue);
    }
  }

  private DurableStateBehaviorTestKitApiTest() {}
}
