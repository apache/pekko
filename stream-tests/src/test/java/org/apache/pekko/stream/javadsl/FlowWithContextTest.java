/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl;

import static org.apache.pekko.NotUsed.notUsed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.pf.PFBuilder;
import org.apache.pekko.stream.StreamTestJupiter;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class FlowWithContextTest extends StreamTestJupiter {

  public FlowWithContextTest() {
    super(actorSystemResource);
  }

  @RegisterExtension
  static PekkoJUnitJupiterActorSystemResource actorSystemResource =
      new PekkoJUnitJupiterActorSystemResource("FlowWithContextTest", PekkoSpec.testConf());

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

  @Test
  public void mustPassContextThroughAdditionalFilteringOperators() throws Exception {
    final FlowWithContext<Object, String, Integer, String, NotUsed> collectFlow =
        FlowWithContext.<Object, String>create()
            .collectType(Integer.class)
            .collectWhile(
                PFBuilder.<Integer, Integer>create()
                    .match(Integer.class, value -> value < 3, value -> value * 10)
                    .build());

    final CompletionStage<List<Pair<Integer, String>>> collectResult =
        Source.from(
                List.of(
                    new Pair<Object, String>(1, "one"),
                    new Pair<Object, String>(2, "two"),
                    new Pair<Object, String>("three", "three-string"),
                    new Pair<Object, String>(3, "three")))
            .via(collectFlow.asFlow())
            .runWith(Sink.seq(), system);

    assertEquals(
        List.of(new Pair<>(10, "one"), new Pair<>(20, "two")),
        collectResult.toCompletableFuture().get(3, TimeUnit.SECONDS));

    final FlowWithContext<Object, String, Integer, String, NotUsed> collectFirstFlow =
        FlowWithContext.<Object, String>create()
            .collectFirst(
                PFBuilder.<Object, Integer>create()
                    .match(Integer.class, value -> value > 1, value -> value * 10)
                    .build());

    final CompletionStage<List<Pair<Integer, String>>> collectFirstResult =
        Source.from(
                List.of(
                    new Pair<Object, String>(1, "one"),
                    new Pair<Object, String>(2, "two"),
                    new Pair<Object, String>(3, "three")))
            .via(collectFirstFlow.asFlow())
            .runWith(Sink.seq(), system);

    assertEquals(
        List.of(new Pair<>(20, "two")),
        collectFirstResult.toCompletableFuture().get(3, TimeUnit.SECONDS));

    final FlowWithContext<Integer, String, Integer, String, NotUsed> mapOptionFlow =
        FlowWithContext.<Integer, String>create()
            .mapOption(value -> value == 0 ? Optional.empty() : Optional.of(value * 10));

    final CompletionStage<List<Pair<Integer, String>>> mapOptionResult =
        Source.from(
                List.of(
                    new Pair<>(0, "zero"),
                    new Pair<>(1, "one"),
                    new Pair<>(2, "two"),
                    new Pair<>(3, "three")))
            .via(mapOptionFlow.asFlow())
            .runWith(Sink.seq(), system);

    assertEquals(
        List.of(new Pair<>(10, "one"), new Pair<>(20, "two"), new Pair<>(30, "three")),
        mapOptionResult.toCompletableFuture().get(3, TimeUnit.SECONDS));
  }

  @Test
  public void mustPassContextThroughTruncatingOperators() throws Exception {
    final FlowWithContext<Integer, String, Integer, String, NotUsed> flow =
        FlowWithContext.<Integer, String>create()
            .drop(1)
            .dropRepeated()
            .dropWhile(value -> value < 2)
            .takeUntil(value -> value == 3)
            .takeWithin(Duration.ofDays(1))
            .take(2)
            .limit(2)
            .limitWeighted(2, value -> 1L);

    final CompletionStage<List<Pair<Integer, String>>> result =
        Source.from(
                List.of(
                    new Pair<>(0, "zero"),
                    new Pair<>(1, "one"),
                    new Pair<>(1, "one-duplicate"),
                    new Pair<>(2, "two"),
                    new Pair<>(3, "three"),
                    new Pair<>(4, "four")))
            .via(flow.asFlow())
            .runWith(Sink.seq(), system);

    assertEquals(
        List.of(new Pair<>(2, "two"), new Pair<>(3, "three")),
        result.toCompletableFuture().get(3, TimeUnit.SECONDS));

    final CompletionStage<List<Pair<Integer, String>>> inclusiveResult =
        Source.from(List.of(new Pair<>(1, "one"), new Pair<>(2, "two"), new Pair<>(3, "three")))
            .via(
                FlowWithContext.<Integer, String>create()
                    .takeWhile(value -> value < 2, true)
                    .asFlow())
            .runWith(Sink.seq(), system);

    assertEquals(
        List.of(new Pair<>(1, "one"), new Pair<>(2, "two")),
        inclusiveResult.toCompletableFuture().get(3, TimeUnit.SECONDS));

    final CompletionStage<List<Pair<Integer, String>>> customPredicateResult =
        Source.from(List.of(new Pair<>(1, "one"), new Pair<>(3, "three"), new Pair<>(4, "four")))
            .via(
                FlowWithContext.<Integer, String>create()
                    .dropRepeated((left, right) -> left % 2 == right % 2)
                    .takeWhile(value -> value < 3)
                    .asFlow())
            .runWith(Sink.seq(), system);

    assertEquals(
        List.of(new Pair<>(1, "one")),
        customPredicateResult.toCompletableFuture().get(3, TimeUnit.SECONDS));

    final CompletionStage<List<Pair<Integer, String>>> timedResult =
        Source.single(new Pair<>(1, "one"))
            .initialDelay(Duration.ofMillis(50))
            .via(
                FlowWithContext.<Integer, String>create()
                    .dropWithin(Duration.ofMillis(10))
                    .takeWithin(Duration.ofDays(1))
                    .asFlow())
            .runWith(Sink.seq(), system);

    assertEquals(
        List.of(new Pair<>(1, "one")), timedResult.toCompletableFuture().get(3, TimeUnit.SECONDS));
  }
}
