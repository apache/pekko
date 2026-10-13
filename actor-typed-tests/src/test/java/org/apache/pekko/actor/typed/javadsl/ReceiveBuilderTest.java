/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.javadsl;

import static org.apache.pekko.actor.typed.javadsl.Behaviors.same;

import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Test creating [[MutableActor]]s using [[ReceiveBuilder]] */
@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class ReceiveBuilderTest {

  @JUnitJupiterTestKit public ActorTestKit testKit = new JUnitJupiterTestKitBuilder().build();

  @Test
  public void testMutableCounter() {
    Behavior<BehaviorBuilderTest.CounterMessage> mutable =
        Behaviors.setup(
            context ->
                new AbstractBehavior<BehaviorBuilderTest.CounterMessage>(context) {
                  int currentValue = 0;

                  private Behavior<BehaviorBuilderTest.CounterMessage> receiveIncrease(
                      BehaviorBuilderTest.Increase message) {
                    currentValue++;
                    return this;
                  }

                  private Behavior<BehaviorBuilderTest.CounterMessage> receiveGet(
                      BehaviorBuilderTest.Get get) {
                    get.sender.tell(new BehaviorBuilderTest.Got(currentValue));
                    return this;
                  }

                  @Override
                  public Receive<BehaviorBuilderTest.CounterMessage> createReceive() {
                    return newReceiveBuilder()
                        .onMessage(BehaviorBuilderTest.Increase.class, this::receiveIncrease)
                        .onMessage(BehaviorBuilderTest.Get.class, this::receiveGet)
                        .build();
                  }
                });
  }

  @Test
  public void caseSelectedInOrderAdded() {
    final TestProbe<Object> probe = testKit.createTestProbe();
    Behavior<Object> behavior =
        ReceiveBuilder.<Object>create()
            .onMessage(
                String.class,
                msg -> {
                  probe.ref().tell("handler 1: " + msg);
                  return Behaviors.same();
                })
            .onMessage(
                String.class,
                msg -> {
                  probe.ref().tell("handler 2: " + msg);
                  return Behaviors.same();
                })
            .build();
    ActorRef<Object> ref = testKit.spawn(behavior);
    ref.tell("message");
    probe.expectMessage("handler 1: message");
  }

  @Test
  public void applyPredicate() {
    final TestProbe<Object> probe = testKit.createTestProbe();
    Behavior<Object> behavior =
        ReceiveBuilder.create()
            .onMessage(
                String.class,
                msg -> "other".equals(msg),
                msg -> {
                  probe.ref().tell("handler 1: " + msg);
                  return Behaviors.same();
                })
            .onMessage(
                String.class,
                msg -> {
                  probe.ref().tell("handler 2: " + msg);
                  return Behaviors.same();
                })
            .build();
    ActorRef<Object> ref = testKit.spawn(behavior);
    ref.tell("message");
    probe.expectMessage("handler 2: message");
  }

  @Test
  public void catchAny() {
    final TestProbe<Object> probe = testKit.createTestProbe();
    Behavior<Object> behavior =
        ReceiveBuilder.create()
            .onAnyMessage(
                msg -> {
                  probe.ref().tell(msg);
                  return same();
                })
            .build();
    ActorRef<Object> ref = testKit.spawn(behavior);
    ref.tell("message");
    probe.expectMessage("message");
  }

  public void compileOnlyHandlerAllowedToThrowCheckedException() {
    Behavior<Object> behavior =
        ReceiveBuilder.create()
            .onMessageEquals(
                "exactly",
                () -> {
                  throw new Exception("checked");
                })
            .onMessage(
                String.class,
                msg -> {
                  throw new Exception("checked");
                })
            .onMessage(
                Integer.class,
                msg -> true,
                msg -> {
                  throw new Exception("checked");
                })
            .onSignalEquals(
                PostStop.instance(),
                () -> {
                  throw new Exception("checked");
                })
            .onSignal(
                PostStop.class,
                (signal) -> {
                  throw new Exception("checked");
                })
            .onSignal(
                PostStop.class,
                signal -> true,
                signal -> {
                  throw new Exception("checked");
                })
            .build();
  }
}
