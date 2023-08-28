/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) since 2016 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.javadsl.cookbook;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecipeSplitter {
  private static ActorSystem system;

  @Test
  public void simpleSplit() throws ExecutionException, InterruptedException {
    // #Simple-Split
    // Sample Source
    Source<String, NotUsed> source = Source.from(Arrays.asList("1-2-3", "2-3", "3-4"));

    CompletionStage<List<Integer>> ret =
        source
            .map(s -> Arrays.asList(s.split("-")))
            .mapConcat(f -> f)
            // Sub-streams logic
            .map(s -> Integer.valueOf(s))
            .runWith(Sink.seq(), system);

    // Verify results
    List<Integer> list = ret.toCompletableFuture().get();
    assert list.equals(Arrays.asList(1, 2, 3, 2, 3, 3, 4));
    // #Simple-Split
  }

  @Test
  public void splitAggregate() throws ExecutionException, InterruptedException {
    // #Aggregate-Split
    // Sample Source
    Source<String, NotUsed> source = Source.from(Arrays.asList("1-2-3", "2-3", "3-4"));

    CompletionStage<List<Integer>> ret =
        source
            .map(s -> Arrays.asList(s.split("-")))
            // split all messages into sub-streams
            .splitWhen(a -> true)
            // now split each collection
            .mapConcat(f -> f)
            // Sub-streams logic
            .map(s -> Integer.valueOf(s))
            // aggregate each sub-stream
            .reduce((a, b) -> a + b)
            // and merge back the result into the original stream
            .mergeSubstreams()
            .runWith(Sink.seq(), system);

    // Verify results
    List<Integer> list = ret.toCompletableFuture().get();
    assert list.equals(Arrays.asList(6, 5, 7));
    // #Aggregate-Split
  }

  @BeforeClass
  public static void setup() throws Exception {
    system = ActorSystem.create();
  }

  @AfterClass
  public static void teardown() throws Exception {
    TestKit.shutdownActorSystem(system);
  }
}
