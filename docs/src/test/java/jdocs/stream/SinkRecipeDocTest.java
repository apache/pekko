/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.Sink;
import jdocs.AbstractJavaTest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SinkRecipeDocTest extends AbstractJavaTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("SinkRecipeDocTest");
  }

  @Test
  public void foreachAsync() {
    final Function<Integer, CompletionStage<Void>> asyncProcessing =
        param -> CompletableFuture.completedFuture(param).thenAccept(System.out::println);

    // #forseachAsync-processing
    // final Function<Integer, CompletionStage<Void>> asyncProcessing = _

    final Source<Integer, NotUsed> numberSource = Source.range(1, 100);

    numberSource.runWith(Sink.foreachAsync(10, asyncProcessing), system);
    // #forseachAsync-processing
  }
}
