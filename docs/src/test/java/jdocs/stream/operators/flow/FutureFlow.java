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

package jdocs.stream.operators.flow;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;

public class FutureFlow {

  private ActorSystem system = null;

  // #base-on-first-element
  CompletionStage<Flow<Integer, String, NotUsed>> processingFlow(int id) {
    return CompletableFuture.completedFuture(
        Flow.of(Integer.class).map(n -> "id: " + id + " value: " + n));
  }
  // #base-on-first-element

  public void compileOnlyBaseOnFirst() {
    // #base-on-first-element

    Source<String, NotUsed> source =
        Source.range(1, 10)
            .prefixAndTail(1)
            .flatMapConcat(
                (pair) -> {
                  List<Integer> head = pair.first();
                  Source<Integer, NotUsed> tail = pair.second();

                  int id = head.get(0);

                  return tail.via(Flow.completionStageFlow(processingFlow(id)));
                });
    // #base-on-first-element
  }
}
