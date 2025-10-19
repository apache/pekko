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

package org.apache.pekko.stream.javadsl;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.stream.ThrottleMode;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

public class FlowWithContextThrottleTest extends StreamTest {

  public FlowWithContextThrottleTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("ThrottleTest", PekkoSpec.testConf());

  @Test
  public void mustWorksForTwoStreams() throws Exception {
    final FlowWithContext<Integer, String, Integer, String, NotUsed> sharedThrottle =
        FlowWithContext.<Integer, String>create()
            .throttle(1, java.time.Duration.ofDays(1), 1, (a) -> 1, ThrottleMode.enforcing());

    CompletionStage<List<Pair<Integer, String>>> result1 =
        Source.single(new Pair<>(1, "context-a"))
            .via(sharedThrottle.asFlow())
            .via(sharedThrottle.asFlow())
            .runWith(Sink.seq(), system);

    // If there is accidental shared state then we would not be able to pass through the single
    // element
    List<Pair<Integer, String>> pairs1 = result1.toCompletableFuture().get(3, TimeUnit.SECONDS);

    assertEquals(1, pairs1.size());
    assertEquals(Integer.valueOf(1), pairs1.get(0).first());
    assertEquals("context-a", pairs1.get(0).second());

    // It works with a new stream, too
    CompletionStage<List<Pair<Integer, String>>> result2 =
        Source.single(new Pair<>(2, "context-b"))
            .via(sharedThrottle.asFlow())
            .via(sharedThrottle.asFlow())
            .runWith(Sink.seq(), system);

    List<Pair<Integer, String>> pairs2 = result2.toCompletableFuture().get(3, TimeUnit.SECONDS);

    assertEquals(1, pairs2.size());
    assertEquals(Integer.valueOf(2), pairs2.get(0).first());
    assertEquals("context-b", pairs2.get(0).second());
  }
}
