/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.stream.ThrottleMode;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class FlowThrottleTest extends StreamTest {
  public FlowThrottleTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("ThrottleTest", PekkoSpec.testConf());

  @Test
  public void mustWorksForTwoStreams() throws Exception {
    final Flow<Integer, Integer, NotUsed> sharedThrottle =
        Flow.of(Integer.class)
            .throttle(1, java.time.Duration.ofDays(1), 1, ThrottleMode.enforcing());

    CompletionStage<List<Integer>> result1 =
        Source.single(1).via(sharedThrottle).via(sharedThrottle).runWith(Sink.seq(), system);

    // If there is accidental shared state then we would not be able to pass through the single
    // element
    assertEquals(
        result1.toCompletableFuture().get(3, TimeUnit.SECONDS), Collections.singletonList(1));

    // It works with a new stream, too
    CompletionStage<List<Integer>> result2 =
        Source.single(1).via(sharedThrottle).via(sharedThrottle).runWith(Sink.seq(), system);

    assertEquals(
        result2.toCompletableFuture().get(3, TimeUnit.SECONDS), Collections.singletonList(1));
  }
}
