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

package org.apache.pekko.persistence.typed.state.javadsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.pekko.Done;
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.persistence.state.DurableStateStoreRegistry;
import org.apache.pekko.persistence.state.javadsl.DurableStateUpdateStore;
import org.apache.pekko.persistence.state.javadsl.GetObjectResult;
import org.apache.pekko.persistence.testkit.state.PersistenceTestKitDurableStateStoreProvider;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class RuntimeDurableStateStoreTest {

  @JUnitJupiterTestKit public ActorTestKit testKit = new JUnitJupiterTestKitBuilder().build();

  static Config config(String store) {
    return ConfigFactory.parseString(
        store
            + " {\n"
            + "  state.class = \""
            + PersistenceTestKitDurableStateStoreProvider.class.getName()
            + "\"\n"
            + "}\n");
  }

  interface Command {}

  static final class Save implements Command {
    final String text;
    final ActorRef<Done> replyTo;

    Save(String text, ActorRef<Done> replyTo) {
      this.text = text;
      this.replyTo = replyTo;
    }
  }

  static final class ShowMeWhatYouGot implements Command {
    final ActorRef<String> replyTo;

    ShowMeWhatYouGot(ActorRef<String> replyTo) {
      this.replyTo = replyTo;
    }
  }

  enum Stop implements Command {
    INSTANCE
  }

  static final class Actor extends DurableStateBehavior<Command, String> {
    private final String store;

    static Behavior<Command> create(String persistenceId, String store) {
      return new Actor(persistenceId, store);
    }

    private Actor(String persistenceId, String store) {
      super(PersistenceId.ofUniqueId(persistenceId));
      this.store = store;
    }

    @Override
    public String emptyState() {
      return "";
    }

    @Override
    public CommandHandler<Command, String> commandHandler() {
      return newCommandHandlerBuilder()
          .forAnyState()
          .onCommand(Save.class, this::onSave)
          .onCommand(ShowMeWhatYouGot.class, this::onShow)
          .onCommand(Stop.class, (state, cmd) -> Effect().stop())
          .build();
    }

    private Effect<String> onSave(String state, Save cmd) {
      String newState =
          Stream.of(state, cmd.text).filter(s -> !s.isEmpty()).collect(Collectors.joining("|"));
      return Effect().persist(newState).thenRun(() -> cmd.replyTo.tell(Done.getInstance()));
    }

    private Effect<String> onShow(String state, ShowMeWhatYouGot cmd) {
      cmd.replyTo.tell(state);
      return Effect().none();
    }

    @Override
    public String durableStateStorePluginId() {
      return store + ".state";
    }

    @Override
    public Optional<Config> durableStateStorePluginConfig() {
      return Optional.of(config(store));
    }
  }

  @Test
  public void configureAtRuntimeInIsolatedInstances() throws Exception {
    TestProbe<Done> probe = testKit.createTestProbe();

    // one actor in each store with same id
    ActorRef<Command> s1 = testKit.spawn(Actor.create("id1", "store1"));
    ActorRef<Command> s2 = testKit.spawn(Actor.create("id1", "store2"));
    s1.tell(new Save("s1m1", probe.ref()));
    probe.receiveMessage();
    s2.tell(new Save("s2m1", probe.ref()));
    probe.receiveMessage();

    assertStore("store1", "s1m1");
    assertStore("store2", "s2m1");
  }

  private void assertStore(String store, String expectedState) throws Exception {
    @SuppressWarnings("unchecked")
    DurableStateUpdateStore<String> durableStateStore =
        DurableStateStoreRegistry.get(testKit.system())
            .getDurableStateStoreFor(
                DurableStateUpdateStore.class, store + ".state", config(store));
    GetObjectResult<String> result =
        durableStateStore.getObject("id1").toCompletableFuture().get(3, TimeUnit.SECONDS);
    assertEquals(Optional.of(expectedState), result.value());
  }
}
