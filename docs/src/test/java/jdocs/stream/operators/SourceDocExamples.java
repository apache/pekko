/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators;

// #imports
// #range-imports
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.testkit.typed.javadsl.ManualTime;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitResource;
import org.apache.pekko.stream.javadsl.Source;
// #range-imports

// #actor-ref-imports
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.CompletionStrategy;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.testkit.TestProbe;
// #actor-ref-imports

// #maybe
import org.apache.pekko.stream.javadsl.RunnableGraph;
import java.util.concurrent.CompletableFuture;
// #maybe

import java.util.Arrays;
import java.util.Optional;

// #imports

public class SourceDocExamples {

  public static final TestKitJUnitResource testKit = new TestKitJUnitResource(ManualTime.config());

  public static void fromExample() {
    final ActorSystem system = null;

    // #source-from-example
    Source<Integer, NotUsed> ints = Source.from(Arrays.asList(0, 1, 2, 3, 4, 5));
    ints.runForeach(System.out::println, system);

    String text =
        "Perfection is finally attained not when there is no longer more to add,"
            + "but when there is no longer anything to take away.";
    Source<String, NotUsed> words = Source.from(Arrays.asList(text.split("\\s")));
    words.runForeach(System.out::println, system);
    // #source-from-example
  }

  static void rangeExample() {

    final ActorSystem system = ActorSystem.create("Source");

    // #range

    Source<Integer, NotUsed> source = Source.range(1, 100);

    // #range

    // #range
    Source<Integer, NotUsed> sourceStepFive = Source.range(1, 100, 5);

    // #range

    // #range
    Source<Integer, NotUsed> sourceStepNegative = Source.range(100, 1, -1);
    // #range

    // #run-range
    source.runForeach(i -> System.out.println(i), system);
    // #run-range
  }

  static void actorRef() {
    final ActorSystem system = null;

    // #actor-ref

    int bufferSize = 100;
    Source<Object, ActorRef> source =
        Source.actorRef(
            elem -> {
              // complete stream immediately if we send it Done
              if (elem == Done.done()) return Optional.of(CompletionStrategy.immediately());
              else return Optional.empty();
            },
            // never fail the stream because of a message
            elem -> Optional.empty(),
            bufferSize,
            OverflowStrategy.dropHead());

    ActorRef actorRef = source.to(Sink.foreach(System.out::println)).run(system);
    actorRef.tell("hello", ActorRef.noSender());
    actorRef.tell("hello", ActorRef.noSender());

    // The stream completes successfully with the following message
    actorRef.tell(Done.done(), ActorRef.noSender());
    // #actor-ref
  }

  static void actorRefWithBackpressure() {
    final TestProbe probe = null;
    final ActorSystem system = null;

    // #actorRefWithBackpressure
    Source<String, ActorRef> source =
        Source.<String>actorRefWithBackpressure(
            "ack",
            // complete when we send "complete"
            o -> {
              if (o == "complete") return Optional.of(CompletionStrategy.draining());
              else return Optional.empty();
            },
            // do not fail on any message
            o -> Optional.empty());

    ActorRef actorRef = source.to(Sink.foreach(System.out::println)).run(system);
    probe.send(actorRef, "hello");
    probe.expectMsg("ack");
    probe.send(actorRef, "hello");
    probe.expectMsg("ack");

    // The stream completes successfully with the following message
    actorRef.tell("complete", ActorRef.noSender());
    // #actorRefWithBackpressure
  }

  static void maybeExample() {
    final ActorSystem system = null;

    // #maybe
    Source<Integer, CompletableFuture<Optional<Integer>>> source = Source.<Integer>maybe();
    RunnableGraph<CompletableFuture<Optional<Integer>>> runnable =
        source.to(Sink.foreach(System.out::println));

    CompletableFuture<Optional<Integer>> completable1 = runnable.run(system);
    completable1.complete(Optional.of(1)); // prints 1

    CompletableFuture<Optional<Integer>> completable2 = runnable.run(system);
    completable2.complete(Optional.of(2)); // prints 2
    // #maybe
  }

  static
  // #maybe-signature
  <Out> Source<Out, CompletableFuture<Optional<Out>>> maybe()
        // #maybe-signature
      {
    return Source.maybe();
  }
}
