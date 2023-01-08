/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.apache.pekko.NotUsed.notUsed;
import static org.junit.Assert.assertEquals;

public class FlowWithContextTest extends StreamTest {

  public FlowWithContextTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("FlowWithContextTest", PekkoSpec.testConf());

  @Test
  public void simpleCaseHappyPath() throws Exception {
    final FlowWithContext<Integer, String, Integer, String, NotUsed> flow =
        FlowWithContext.create();

    final CompletionStage<List<Pair<Integer, String>>> result =
        Source.single(new Pair<>(1, "context"))
            .via(flow.map(n -> n + 1).mapContext(ctx -> ctx + "-mapped"))
            .runWith(Sink.seq(), system);
    final List<Pair<Integer, String>> pairs = result.toCompletableFuture().get(3, TimeUnit.SECONDS);
    assertEquals(1, pairs.size());
    assertEquals(Integer.valueOf(2), pairs.get(0).first());
    assertEquals("context-mapped", pairs.get(0).second());
  }

  @Test
  public void mustAllowComposingFlows() throws Exception {
    final FlowWithContext<Integer, NotUsed, Integer, NotUsed, NotUsed> flow1 =
        FlowWithContext.create();
    final FlowWithContext<Integer, NotUsed, String, NotUsed, NotUsed> flow2 =
        FlowWithContext.<Integer, NotUsed>create().map(Object::toString);

    final FlowWithContext<Integer, NotUsed, String, NotUsed, NotUsed> flow3 = flow1.via(flow2);

    final CompletionStage<List<Pair<String, NotUsed>>> result =
        Source.single(new Pair<>(1, notUsed())).via(flow3.asFlow()).runWith(Sink.seq(), system);

    List<Pair<String, NotUsed>> pairs = result.toCompletableFuture().get(3, TimeUnit.SECONDS);

    assertEquals(1, pairs.size());
    assertEquals("1", pairs.get(0).first());
    assertEquals(notUsed(), pairs.get(0).second());
  }
}
