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
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class DurableStateReplyEffectTest {
  @JUnitJupiterTestKit
  public ActorTestKit testKit =
      new JUnitJupiterTestKitBuilder()
          .withCustomConfig(PersistenceTestKitDurableStateStorePlugin.getInstance().config())
          .build();

  private static <Self extends ReplyEffectOps<Integer, Self>> Self unstash(
      ReplyEffectOps<Integer, Self> effect) {
    return effect.thenUnstashAll();
  }

  private ActorRef<ActorRef<Integer>> spawnStoppingBehavior(
      String persistenceId,
      BiFunction<EffectFactories<Integer>, ActorRef<Integer>, ReplyEffectWithStop<Integer>>
          effect) {
    return testKit.spawn(
        new DurableStateBehaviorWithEnforcedReplies<ActorRef<Integer>, Integer>(
            PersistenceId.ofUniqueId(persistenceId)) {
          @Override
          public Integer emptyState() {
            return 0;
          }

          @Override
          public CommandHandlerWithReply<ActorRef<Integer>, Integer> commandHandler() {
            return (state, replyTo) -> effect.apply(Effect(), replyTo).thenStop();
          }
        });
  }

  @Test
  public void persistThenReplyThenStop() {
    TestProbe<Integer> probe = testKit.createTestProbe();
    ActorRef<ActorRef<Integer>> actor =
        spawnStoppingBehavior(
            "persist-then-reply-then-stop",
            (effect, replyTo) -> effect.persist(1).thenReply(replyTo, state -> state));
    actor.tell(probe.ref());
    probe.expectMessage(1);
    probe.expectTerminated(actor);
  }

  @Test
  public void persistThenReplyWithFunctionOverSupertypeOfState() {
    // a reply function typed over a supertype of the state can be passed as is
    final Function<Number, Integer> doubled = n -> n.intValue() * 2;
    TestProbe<Integer> probe = testKit.createTestProbe();
    ActorRef<ActorRef<Integer>> actor =
        spawnStoppingBehavior(
            "persist-then-reply-with-function-over-supertype",
            (effect, replyTo) -> effect.persist(21).thenReply(replyTo, doubled));
    actor.tell(probe.ref());
    probe.expectMessage(42);
    probe.expectTerminated(actor);
  }

  @Test
  public void replyThenStop() {
    TestProbe<Integer> probe = testKit.createTestProbe();
    ActorRef<ActorRef<Integer>> actor =
        spawnStoppingBehavior("reply-then-stop", (effect, replyTo) -> effect.reply(replyTo, 42));
    actor.tell(probe.ref());
    probe.expectMessage(42);
    probe.expectTerminated(actor);
  }

  @Test
  public void replyThenUnstashAllThenStop() {
    TestProbe<Integer> probe = testKit.createTestProbe();
    ActorRef<ActorRef<Integer>> actor =
        spawnStoppingBehavior(
            "reply-then-unstash-then-stop",
            (effect, replyTo) -> effect.reply(replyTo, 42).thenUnstashAll());
    actor.tell(probe.ref());
    probe.expectMessage(42);
    probe.expectTerminated(actor);
  }

  @Test
  public void replyThenGenericUnstashThenStop() {
    TestProbe<Integer> probe = testKit.createTestProbe();
    ActorRef<ActorRef<Integer>> actor =
        spawnStoppingBehavior(
            "reply-then-generic-unstash-then-stop",
            (effect, replyTo) -> unstash(effect.reply(replyTo, 42)));
    actor.tell(probe.ref());
    probe.expectMessage(42);
    probe.expectTerminated(actor);
  }

  @Test
  public void noReplyThenStop() {
    TestProbe<Integer> probe = testKit.createTestProbe();
    ActorRef<ActorRef<Integer>> actor =
        spawnStoppingBehavior("no-reply-then-stop", (effect, replyTo) -> effect.noReply());
    actor.tell(probe.ref());
    probe.expectTerminated(actor);
  }
}
