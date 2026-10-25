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

import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class DurableStateReplyEffectThenRunTest {
  @JUnitJupiterTestKit
  public ActorTestKit testKit =
      new JUnitJupiterTestKitBuilder()
          .withCustomConfig(PersistenceTestKitDurableStateStorePlugin.getInstance().config())
          .build();

  private static <Self extends ReplyEffectOps<Number, Self>> Self observe(
      ReplyEffectOps<Number, Self> effect, Procedure<Number> callback) {
    return effect.thenRun(callback);
  }

  private static <Self extends ReplyEffectOps<Number, Self>> Self observe(
      ReplyEffectOps<Number, Self> effect, org.apache.pekko.japi.function.Effect callback) {
    return effect.thenRun(callback);
  }

  private <Command> ActorRef<Command> spawnBehavior(
      String id,
      Function<EffectFactories<Number>, CommandHandlerWithReply<Command, Number>> createHandler) {
    return testKit.spawn(
        new DurableStateBehaviorWithEnforcedReplies<Command, Number>(PersistenceId.ofUniqueId(id)) {
          @Override
          public Number emptyState() {
            return 0;
          }

          @Override
          public CommandHandlerWithReply<Command, Number> commandHandler() {
            return createHandler.apply(Effect());
          }
        });
  }

  @Test
  public void runBothCallbackOverloadsAfterReplyingWithPersistedStateAndBeforeStopping() {
    TestProbe<String> probe = testKit.createTestProbe();
    ActorRef<ActorRef<String>> actor =
        spawnBehavior(
            "persist-reply-run",
            effect ->
                (ignored, replyTo) ->
                    effect
                        .persist(1)
                        .thenReply(replyTo, state -> "reply:" + state)
                        .thenRun((Integer state) -> replyTo.tell("callback:" + state))
                        .thenRun(() -> replyTo.tell("callback-without-state"))
                        .thenStop());
    actor.tell(probe.ref());
    probe.expectMessage("reply:1");
    probe.expectMessage("callback:1");
    probe.expectMessage("callback-without-state");
    probe.expectTerminated(actor);
  }

  @Test
  public void decorateHandlerThatAcceptsOrRejectsCommands() {
    TestProbe<String> probe = testKit.createTestProbe();
    ActorRef<ActorRef<String>> actor =
        spawnBehavior(
            "decorate-reply",
            effect -> {
              BiFunction<Number, ActorRef<String>, ReplyEffectWithStop<Number>> handler =
                  (state, replyTo) -> {
                    if (state.intValue() == 0) {
                      return effect
                          .persist(1)
                          .thenReply(replyTo, updatedState -> "accepted:" + updatedState);
                    } else {
                      return effect.reply(replyTo, "rejected");
                    }
                  };
              return (state, replyTo) ->
                  handler
                      .apply(state, replyTo)
                      .thenRun(updatedState -> replyTo.tell("observed:" + updatedState));
            });

    actor.tell(probe.ref());
    probe.expectMessage("accepted:1");
    probe.expectMessage("observed:1");
    actor.tell(probe.ref());
    probe.expectMessage("rejected");
    probe.expectMessage("observed:1");
  }

  @Test
  public void runCallbackAfterNoReplyAndUnstashAll() {
    TestProbe<String> probe = testKit.createTestProbe();
    ActorRef<ActorRef<String>> actor =
        spawnBehavior(
            "no-reply-run",
            effect ->
                (ignored, replyTo) ->
                    effect
                        .noReply()
                        .thenUnstashAll()
                        .thenRun(state -> replyTo.tell("callback:" + state))
                        .thenStop());
    actor.tell(probe.ref());
    probe.expectMessage("callback:0");
    probe.expectTerminated(actor);
  }

  @Test
  public void finishCallbacksAroundUnstashAllBeforeProcessingStashedCommands() {
    TestProbe<String> probe = testKit.createTestProbe();
    ActorRef<String> actor =
        spawnBehavior(
            "unstash-run",
            effect ->
                (state, command) -> {
                  if (command.equals("stash") && state.intValue() == 0) {
                    probe.ref().tell("stashed");
                    return effect.stash();
                  } else if (command.equals("release")) {
                    return effect
                        .persist(1)
                        .thenReply(probe.ref(), updatedState -> "reply:" + updatedState)
                        .thenRun(
                            (Integer updatedState) ->
                                probe.ref().tell("before-unstash:" + updatedState))
                        .thenUnstashAll()
                        .thenRun(() -> probe.ref().tell("after-unstash"));
                  } else {
                    return effect.reply(probe.ref(), "unstashed:" + state).thenStop();
                  }
                });

    actor.tell("stash");
    probe.expectMessage("stashed");
    actor.tell("release");
    probe.expectMessage("reply:1");
    probe.expectMessage("before-unstash:1");
    probe.expectMessage("after-unstash");
    probe.expectMessage("unstashed:1");
    probe.expectTerminated(actor);
  }

  @Test
  public void preserveStageThroughGenericCallbackHelpersAfterThenNoReply() {
    TestProbe<String> probe = testKit.createTestProbe();
    ActorRef<ActorRef<String>> actor =
        spawnBehavior(
            "generic-then-no-reply-run",
            effect ->
                (ignored, replyTo) ->
                    observe(
                            observe(
                                effect.persist(1).thenNoReply(),
                                state -> replyTo.tell("callback:" + state)),
                            () -> replyTo.tell("callback-without-state"))
                        .thenUnstashAll()
                        .thenStop());
    actor.tell(probe.ref());
    probe.expectMessage("callback:1");
    probe.expectMessage("callback-without-state");
    probe.expectTerminated(actor);
  }
}
