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

package org.apache.pekko.actor.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.TestException;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.pattern.StatusReply;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.time.Duration;

public class ActorContextAskTest extends JUnitSuite {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(PekkoSpec.testConf());

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  static class Ping {
    final ActorRef<Pong> replyTo;

    public Ping(ActorRef<Pong> replyTo) {
      this.replyTo = replyTo;
    }
  }

  static class Pong {}

  @Test
  public void provideASafeAsk() {
    final Behavior<Ping> pingPongBehavior =
        Behaviors.receive(
            (ActorContext<Ping> context, Ping message) -> {
              message.replyTo.tell(new Pong());
              return Behaviors.same();
            });

    final ActorRef<Ping> pingPong = testKit.spawn(pingPongBehavior);
    final TestProbe<Object> probe = testKit.createTestProbe();

    final Behavior<Object> snitch =
        Behaviors.setup(
            (ActorContext<Object> context) -> {
              context.ask(
                  Pong.class,
                  pingPong,
                  Duration.ofSeconds(3),
                  (ActorRef<Pong> ref) -> new Ping(ref),
                  (pong, exception) -> {
                    if (pong != null) return pong;
                    else return exception;
                  });

              return Behaviors.receiveMessage(
                  (Object message) -> {
                    probe.ref().tell(message);
                    return Behaviors.same();
                  });
            });

    testKit.spawn(snitch);

    probe.expectMessageClass(Pong.class);
  }

  static class PingWithStatus {
    final ActorRef<StatusReply<Pong>> replyTo;

    public PingWithStatus(ActorRef<StatusReply<Pong>> replyTo) {
      this.replyTo = replyTo;
    }
  }

  @Test
  public void askWithStatusUnwrapsSuccess() {
    final TestProbe<Object> probe = testKit.createTestProbe();

    testKit.spawn(
        Behaviors.<Pong>setup(
            context -> {
              context.askWithStatus(
                  Pong.class,
                  probe.getRef(),
                  Duration.ofSeconds(3),
                  PingWithStatus::new,
                  (pong, failure) -> {
                    if (pong != null) return pong;
                    else throw new RuntimeException(failure);
                  });

              return Behaviors.receive(Pong.class)
                  .onAnyMessage(
                      pong -> {
                        probe.ref().tell("got pong");
                        return Behaviors.same();
                      })
                  .build();
            }));

    ActorRef<StatusReply<Pong>> replyTo = probe.expectMessageClass(PingWithStatus.class).replyTo;

    replyTo.tell(StatusReply.success(new Pong()));
    probe.expectMessage("got pong");
  }

  private static Behavior<Throwable> exceptionCapturingBehavior(ActorRef<Object> probe) {
    return Behaviors.setup(
        context -> {
          context.askWithStatus(
              Pong.class,
              probe.narrow(),
              Duration.ofSeconds(3),
              PingWithStatus::new,
              (pong, failure) -> {
                if (pong != null) throw new IllegalArgumentException("did not expect pong");
                else return failure;
              });

          return Behaviors.receive(Throwable.class)
              .onAnyMessage(
                  throwable -> {
                    probe.tell(
                        "got error: "
                            + throwable.getClass().getName()
                            + ", "
                            + throwable.getMessage());
                    return Behaviors.same();
                  })
              .build();
        });
  }

  @Test
  public void askWithStatusUnwrapsErrorMessages() {
    final TestProbe<Object> probe = testKit.createTestProbe();
    testKit.spawn(exceptionCapturingBehavior(probe.getRef()));
    ActorRef<StatusReply<Pong>> replyTo = probe.expectMessageClass(PingWithStatus.class).replyTo;

    replyTo.tell(StatusReply.error("boho"));
    probe.expectMessage("got error: org.apache.pekko.pattern.StatusReply$ErrorMessage, boho");
  }

  @Test
  public void askWithStatusUnwrapsErrorCustomExceptions() {
    final TestProbe<Object> probe = testKit.createTestProbe();
    testKit.spawn(exceptionCapturingBehavior(probe.getRef()));
    ActorRef<StatusReply<Pong>> replyTo = probe.expectMessageClass(PingWithStatus.class).replyTo;

    // with custom exception
    replyTo.tell(StatusReply.error(new TestException("boho")));
    probe.expectMessage("got error: org.apache.pekko.actor.testkit.typed.TestException, boho");
  }
}
