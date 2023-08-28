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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.stream.ThrottleMode;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

public class SourceWithContextThrottleTest extends StreamTest {

  public SourceWithContextThrottleTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("ThrottleTest", PekkoSpec.testConf());

  @Test
  public void mustBeAbleToUseThrottle() throws Exception {
    List<Pair<Integer, String>> list =
        Arrays.asList(
            new Pair<>(0, "context-a"), new Pair<>(1, "context-b"), new Pair<>(2, "context-c"));
    Pair<Integer, String> result =
        SourceWithContext.fromPairs(Source.from(list))
            .throttle(10, Duration.ofSeconds(1), 10, ThrottleMode.shaping())
            .throttle(10, Duration.ofSeconds(1), 10, ThrottleMode.enforcing())
            .runWith(Sink.head(), system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

    assertEquals(list.get(0), result);
  }
}
