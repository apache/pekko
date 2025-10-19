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

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

public class SetupTest extends StreamTest {
  public SetupTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("SetupTest", PekkoSpec.testConf());

  @Test
  public void shouldExposeMaterializerAndAttributesToSource() throws Exception {
    final Source<Pair<Boolean, Boolean>, CompletionStage<NotUsed>> source =
        Source.fromMaterializer(
            (mat, attr) ->
                Source.single(Pair.create(mat.isShutdown(), attr.attributeList().isEmpty())));

    assertEquals(
        Pair.create(false, false),
        source.runWith(Sink.head(), system).toCompletableFuture().get(5, TimeUnit.SECONDS));
  }

  @Test
  public void shouldExposeMaterializerAndAttributesToFlow() throws Exception {
    final Flow<Object, Pair<Boolean, Boolean>, CompletionStage<NotUsed>> flow =
        Flow.fromMaterializer(
            (mat, attr) ->
                Flow.fromSinkAndSource(
                    Sink.ignore(),
                    Source.single(Pair.create(mat.isShutdown(), attr.attributeList().isEmpty()))));

    assertEquals(
        Pair.create(false, false),
        Source.empty()
            .via(flow)
            .runWith(Sink.head(), system)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS));
  }

  @Test
  public void shouldExposeMaterializerAndAttributesToSink() throws Exception {
    Sink<Object, CompletionStage<CompletionStage<Pair<Boolean, Boolean>>>> sink =
        Sink.fromMaterializer(
            (mat, attr) ->
                Sink.fold(
                    Pair.create(mat.isShutdown(), attr.attributeList().isEmpty()), Keep.left()));

    assertEquals(
        Pair.create(false, false),
        Source.empty()
            .runWith(sink, system)
            .thenCompose(c -> c)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS));
  }
}
