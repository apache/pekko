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

package jdocs.stream.operators.source;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.SinkQueueWithCancel;
import org.apache.pekko.stream.javadsl.Source;

public class Lazy {

  private ActorSystem system = null;

  private Source<String, NotUsed> createExpensiveSource() {
    throw new UnsupportedOperationException("Not implemented in sample");
  }

  void notReallyThatLazy() {
    // #not-a-good-example
    Source<String, CompletionStage<NotUsed>> source =
        Source.lazySource(
            () -> {
              System.out.println("Creating the actual source");
              return createExpensiveSource();
            });

    SinkQueueWithCancel<String> queue = source.runWith(Sink.queue(), system);

    // ... time passes ...
    // at some point in time we pull the first time
    // but the source creation may already have been triggered
    queue.pull();
    // #not-a-good-example
  }

  static class IteratorLikeThing {
    boolean thereAreMore() {
      throw new UnsupportedOperationException("Not implemented in sample");
    }

    String extractNext() {
      throw new UnsupportedOperationException("Not implemented in sample");
    }
  }

  void safeMutableSource() {
    // #one-per-materialization
    RunnableGraph<CompletionStage<NotUsed>> stream =
        Source.lazySource(
                () -> {
                  IteratorLikeThing instance = new IteratorLikeThing();
                  return Source.unfold(
                      instance,
                      sameInstance -> {
                        if (sameInstance.thereAreMore())
                          return Optional.of(Pair.create(sameInstance, sameInstance.extractNext()));
                        else return Optional.empty();
                      });
                })
            .to(Sink.foreach(System.out::println));

    // each of the three materializations will have their own instance of IteratorLikeThing
    stream.run(system);
    stream.run(system);
    stream.run(system);
    // #one-per-materialization
  }
}
