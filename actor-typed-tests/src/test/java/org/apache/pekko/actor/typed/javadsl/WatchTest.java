/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.javadsl;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.Done;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.junit.ClassRule;
import org.junit.Rule;
import org.scalatestplus.junit.JUnitSuite;
import org.junit.Test;

import org.apache.pekko.actor.typed.*;

import java.time.Duration;

import static org.apache.pekko.Done.done;
import static org.apache.pekko.actor.typed.javadsl.Behaviors.*;

public class WatchTest extends JUnitSuite {

  @ClassRule public static TestKitJunitResource testKit = new TestKitJunitResource();

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  interface Message {}

  static final class RunTest implements Message {
    private final ActorRef<Done> replyTo;

    public RunTest(ActorRef<Done> replyTo) {
      this.replyTo = replyTo;
    }
  }

  static final class Stop {}

  static final class CustomTerminationMessage implements Message {}

  final Duration timeout = Duration.ofSeconds(5);

  final Behavior<Stop> exitingActor =
      receive(
          (context, message) -> {
            System.out.println("Stopping!");
            return stopped();
          });

  private Behavior<RunTest> waitingForTermination(ActorRef<Done> replyWhenTerminated) {
    return receive(
        (context, message) -> unhandled(),
        (context, sig) -> {
          if (sig instanceof Terminated) {
            replyWhenTerminated.tell(done());
          }
          return same();
        });
  }

  private Behavior<Message> waitingForMessage(ActorRef<Done> replyWhenReceived) {
    return receive(
        (context, message) -> {
          if (message instanceof CustomTerminationMessage) {
            replyWhenReceived.tell(done());
            return same();
          } else {
            return unhandled();
          }
        });
  }

  @Test
  public void shouldWatchTerminatingActor() throws Exception {
    Behavior<RunTest> exiting =
        Behaviors.setup(
            context ->
                Behaviors.receive(RunTest.class)
                    .onMessage(
                        RunTest.class,
                        message -> {
                          ActorRef<Stop> watched = context.spawn(exitingActor, "exitingActor");
                          context.watch(watched);
                          watched.tell(new Stop());
                          return waitingForTermination(message.replyTo);
                        })
                    .build());
    ActorRef<RunTest> exitingRef = testKit.spawn(exiting);

    CompletionStage<Done> result =
        AskPattern.ask(exitingRef, RunTest::new, timeout, testKit.scheduler());
    result.toCompletableFuture().get(3, TimeUnit.SECONDS);
  }

  @Test
  public void shouldWatchWithCustomMessage() throws Exception {
    Behavior<Message> exiting =
        Behaviors.setup(
            context ->
                Behaviors.receive(Message.class)
                    .onMessage(
                        RunTest.class,
                        message -> {
                          ActorRef<Stop> watched = context.spawn(exitingActor, "exitingActor");
                          context.watchWith(watched, new CustomTerminationMessage());
                          watched.tell(new Stop());
                          return waitingForMessage(message.replyTo);
                        })
                    .build());
    ActorRef<Message> exitingRef = testKit.spawn(exiting);

    // Not sure why this does not compile without an explicit cast?
    // system.tell(new RunTest());
    CompletionStage<Done> result =
        AskPattern.ask(exitingRef, RunTest::new, timeout, testKit.scheduler());
    result.toCompletableFuture().get(3, TimeUnit.SECONDS);
  }
}
