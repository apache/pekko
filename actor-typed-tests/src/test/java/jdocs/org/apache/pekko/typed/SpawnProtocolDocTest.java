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

package jdocs.org.apache.pekko.typed;

import jdocs.org.apache.pekko.typed.IntroTest.HelloWorld;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

// #imports1
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

// #imports1

// #imports2
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

// #imports2

public interface SpawnProtocolDocTest {

  // #main
  public abstract class HelloWorldMain {
    private HelloWorldMain() {}

    public static Behavior<SpawnProtocol.Command> create() {
      return Behaviors.setup(
          context -> {
            // Start initial tasks
            // context.spawn(...)

            return SpawnProtocol.create();
          });
    }
  }
  // #main

  public static void main(String[] args) throws Exception {
    // #system-spawn
    final ActorSystem<SpawnProtocol.Command> system =
        ActorSystem.create(HelloWorldMain.create(), "hello");
    final Duration timeout = Duration.ofSeconds(3);

    CompletionStage<ActorRef<HelloWorld.Greet>> greeter =
        AskPattern.ask(
            system,
            replyTo ->
                new SpawnProtocol.Spawn<>(HelloWorld.create(), "greeter", Props.empty(), replyTo),
            timeout,
            system.scheduler());

    Behavior<HelloWorld.Greeted> greetedBehavior =
        Behaviors.receive(
            (context, message) -> {
              context.getLog().info("Greeting for {} from {}", message.whom, message.from);
              return Behaviors.stopped();
            });

    CompletionStage<ActorRef<HelloWorld.Greeted>> greetedReplyTo =
        AskPattern.ask(
            system,
            replyTo -> new SpawnProtocol.Spawn<>(greetedBehavior, "", Props.empty(), replyTo),
            timeout,
            system.scheduler());

    greeter.whenComplete(
        (greeterRef, exc) -> {
          if (exc == null) {
            greetedReplyTo.whenComplete(
                (greetedReplyToRef, exc2) -> {
                  if (exc2 == null) {
                    greeterRef.tell(new HelloWorld.Greet("Pekko", greetedReplyToRef));
                  }
                });
          }
        });

    // #system-spawn

    Thread.sleep(3000);
    system.terminate();
  }
}
