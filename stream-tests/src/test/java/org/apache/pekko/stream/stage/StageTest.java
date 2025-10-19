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

package org.apache.pekko.stream.stage;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

public class StageTest extends StreamTest {
  public StageTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("StageTest", PekkoSpec.testConf());

  @Test
  public void javaStageUsage() throws Exception {
    final java.lang.Iterable<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5);
    final Source<Integer, NotUsed> ints = Source.from(input);
    final JavaIdentityStage<Integer> identity = new JavaIdentityStage<Integer>();

    final CompletionStage<List<Integer>> result =
        ints.via(identity).via(identity).grouped(1000).runWith(Sink.<List<Integer>>head(), system);

    assertEquals(
        Arrays.asList(0, 1, 2, 3, 4, 5), result.toCompletableFuture().get(3, TimeUnit.SECONDS));
  }
}
