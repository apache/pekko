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

import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class LazyAndFutureFlowTest extends StreamTest {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("LazyAndFutureFlowTest", PekkoSpec.testConf());

  public LazyAndFutureFlowTest() {
    super(actorSystemResource);
  }

  // note these are minimal happy path tests to cover API, more thorough tests are on the Scala side

  @Test
  public void completionStageFlow() throws Exception {
    CompletionStage<List<String>> result =
        Source.single("one")
            .via(
                Flow.completionStageFlow(
                    CompletableFuture.completedFuture(Flow.fromFunction(str -> str))))
            .runWith(Sink.seq(), system);

    assertEquals(Arrays.asList("one"), result.toCompletableFuture().get(3, TimeUnit.SECONDS));
  }

  @Test
  public void lazyFlow() throws Exception {
    CompletionStage<List<String>> result =
        Source.single("one")
            .via(Flow.lazyFlow(() -> Flow.fromFunction(str -> str)))
            .runWith(Sink.seq(), system);

    assertEquals(Arrays.asList("one"), result.toCompletableFuture().get(3, TimeUnit.SECONDS));
  }

  @Test
  public void lazyCompletionStageFlow() throws Exception {
    CompletionStage<List<String>> result =
        Source.single("one")
            .via(
                Flow.lazyCompletionStageFlow(
                    () -> CompletableFuture.completedFuture(Flow.fromFunction(str -> str))))
            .runWith(Sink.seq(), system);

    assertEquals(Arrays.asList("one"), result.toCompletableFuture().get(3, TimeUnit.SECONDS));
  }
}
