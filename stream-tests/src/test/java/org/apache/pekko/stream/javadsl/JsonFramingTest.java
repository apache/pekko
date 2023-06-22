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

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.util.ByteString;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

public class JsonFramingTest extends StreamTest {
  public JsonFramingTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("JsonFramingTest", PekkoSpec.testConf());

  @Test
  public void mustBeAbleToParseJsonArray()
      throws InterruptedException, ExecutionException, TimeoutException {
    // #using-json-framing
    String input =
        "[{ \"name\" : \"john\" }, { \"name\" : \"Ég get etið gler án þess að meiða mig\" }, { \"name\" : \"jack\" }]";
    CompletionStage<ArrayList<String>> result =
        Source.single(ByteString.fromString(input))
            .via(JsonFraming.objectScanner(Integer.MAX_VALUE))
            .runFold(
                new ArrayList<String>(),
                (acc, entry) -> {
                  acc.add(entry.utf8String());
                  return acc;
                },
                system);
    // #using-json-framing

    List<String> frames = result.toCompletableFuture().get(5, TimeUnit.SECONDS);
    assertEquals("{ \"name\" : \"john\" }", frames.get(0));
    assertEquals("{ \"name\" : \"Ég get etið gler án þess að meiða mig\" }", frames.get(1));
    assertEquals("{ \"name\" : \"jack\" }", frames.get(2));
  }
}
