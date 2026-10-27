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

package org.apache.pekko.actor.typed.javadsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.FishingOutcome;
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.FishingOutcomes;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.function.Function2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Function values typed over a supertype of the message, or returning a subtype of the result, e.g.
 * shared between behaviors, can be passed to the Java DSL as is.
 */
@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class FunctionWildcardBoundsTest {

  @JUnitJupiterTestKit public ActorTestKit testKit = new JUnitJupiterTestKitBuilder().build();

  @Test
  public void receiveMessageAcceptsHandlerOverSupertype() {
    final TestProbe<Object> probe = testKit.createTestProbe();
    final Function<Object, Behavior<String>> forward =
        msg -> {
          probe.getRef().tell(msg);
          return Behaviors.same();
        };

    final ActorRef<String> ref = testKit.spawn(Behaviors.receiveMessage(forward));
    ref.tell("hello");
    probe.expectMessage("hello");
  }

  @Test
  public void receiveAcceptsHandlerOverSupertypes() {
    final TestProbe<Object> probe = testKit.createTestProbe();
    final Function2<Object, Object, Behavior<String>> forward =
        (context, msg) -> {
          probe.getRef().tell(msg);
          return Behaviors.same();
        };

    final Behavior<String> behavior = Behaviors.receive(forward);
    final ActorRef<String> ref = testKit.spawn(behavior);
    ref.tell("hi");
    probe.expectMessage("hi");
  }

  @Test
  public void behaviorBuilderAcceptsHandlerOverSupertype() {
    final TestProbe<Object> probe = testKit.createTestProbe();
    final Function<CharSequence, Behavior<Object>> length =
        text -> {
          probe.getRef().tell(text.length());
          return Behaviors.same();
        };

    final ActorRef<Object> ref =
        testKit.spawn(Behaviors.receive(Object.class).onMessage(String.class, length).build());
    ref.tell("four");
    probe.expectMessage(4);
  }

  @Test
  public void messageAdapterAcceptsFunctionOverSupertype() {
    final TestProbe<String> probe = testKit.createTestProbe();
    final TestProbe<ActorRef<Integer>> adapterProbe = testKit.createTestProbe();
    final Function<Object, String> describe = Object::toString;

    testKit.spawn(
        Behaviors.<String>setup(
            context -> {
              adapterProbe.getRef().tell(context.messageAdapter(Integer.class, describe));
              return Behaviors.receiveMessage(
                  msg -> {
                    probe.getRef().tell(msg);
                    return Behaviors.same();
                  });
            }));

    adapterProbe.receiveMessage().tell(42);
    probe.expectMessage("42");
  }

  @Test
  public void fishForMessageAcceptsFunctionOverSupertype() {
    final TestProbe<String> probe = testKit.createTestProbe();
    final java.util.function.Function<Object, FishingOutcome> untilDone =
        msg ->
            "done".equals(msg) ? FishingOutcomes.complete() : FishingOutcomes.continueAndIgnore();

    probe.getRef().tell("a");
    probe.getRef().tell("done");
    final List<String> fished = probe.fishForMessage(Duration.ofSeconds(3), untilDone);
    assertEquals(List.of("done"), fished);
  }
}
