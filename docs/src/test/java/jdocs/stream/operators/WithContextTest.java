/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import jdocs.AbstractJavaTest;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
// #imports
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.*;
// #imports
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class WithContextTest extends AbstractJavaTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("WithContextTest");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void documentAsSourceWithContext() throws Exception {
    // #asSourceWithContext

    // values with their contexts as pairs
    Collection<Pair<String, Integer>> values =
        Arrays.asList(Pair.create("eins", 1), Pair.create("zwei", 2), Pair.create("drei", 3));

    // a regular source with pairs as elements
    Source<Pair<String, Integer>, NotUsed> source = Source.from(values);

    // split the pair into stream elements and their context
    SourceWithContext<String, Integer, NotUsed> sourceWithContext =
        source
            .asSourceWithContext(Pair::second) // pick the second pair element as context
            .map(Pair::first); // keep the first pair element as stream element

    SourceWithContext<String, Integer, NotUsed> mapped =
        sourceWithContext
            // regular operators apply to the element without seeing the context
            .map(s -> s.replace('e', 'y'));

    // running the source and asserting the outcome
    CompletionStage<List<Pair<String, Integer>>> result = mapped.runWith(Sink.seq(), system);
    List<Pair<String, Integer>> list = result.toCompletableFuture().get(1, TimeUnit.SECONDS);
    assertThat(
        list, hasItems(Pair.create("yins", 1), Pair.create("zwyi", 2), Pair.create("dryi", 3)));
    // #asSourceWithContext
  }

  @Test
  public void documentAsFlowWithContext() throws Exception {
    // #asFlowWithContext

    // a regular flow with pairs as elements
    Flow<Pair<String, Integer>, Pair<String, Integer>, NotUsed> flow = // ...
        // #asFlowWithContext
        Flow.create();
    // #asFlowWithContext

    // Declare the "flow with context"
    // ingoing: String and Integer
    // outgoing: String and Integer
    FlowWithContext<String, Integer, String, Integer, NotUsed> flowWithContext =
        // convert the flow of pairs into a "flow with context"
        flow.<String, Integer, Integer>asFlowWithContext(
                // at the end of this flow: put the elements and the context back into a pair
                Pair::create,
                // pick the second element of the incoming pair as context
                Pair::second)
            // keep the first pair element as stream element
            .map(Pair::first);

    FlowWithContext<String, Integer, String, Integer, NotUsed> mapped =
        flowWithContext
            // regular operators apply to the element without seeing the context
            .map(s -> s.replace('e', 'y'));

    // running the flow with some sample data and asserting the outcome
    Collection<Pair<String, Integer>> values =
        Arrays.asList(Pair.create("eins", 1), Pair.create("zwei", 2), Pair.create("drei", 3));

    SourceWithContext<String, Integer, NotUsed> source =
        Source.from(values).asSourceWithContext(Pair::second).map(Pair::first);

    CompletionStage<List<Pair<String, Integer>>> result =
        source.via(mapped).runWith(Sink.seq(), system);
    List<Pair<String, Integer>> list = result.toCompletableFuture().get(1, TimeUnit.SECONDS);
    assertThat(
        list, hasItems(Pair.create("yins", 1), Pair.create("zwyi", 2), Pair.create("dryi", 3)));
    // #asFlowWithContext
  }
}
