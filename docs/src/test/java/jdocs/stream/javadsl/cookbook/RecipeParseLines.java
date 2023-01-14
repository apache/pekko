/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.javadsl.cookbook;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Framing;
import org.apache.pekko.stream.javadsl.FramingTruncation;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.ByteString;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class RecipeParseLines extends RecipeTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeParseLines");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void parseLines() throws Exception {
    final Source<ByteString, NotUsed> rawData =
        Source.from(
            Arrays.asList(
                ByteString.fromString("Hello World"),
                ByteString.fromString("\r"),
                ByteString.fromString("!\r"),
                ByteString.fromString("\nHello Akka!\r\nHello Streams!"),
                ByteString.fromString("\r\n\r\n")));

    // #parse-lines
    final Source<String, NotUsed> lines =
        rawData
            .via(Framing.delimiter(ByteString.fromString("\r\n"), 100, FramingTruncation.ALLOW))
            .map(b -> b.utf8String());
    // #parse-lines
    lines.limit(10).runWith(Sink.seq(), system).toCompletableFuture().get(1, TimeUnit.SECONDS);
  }
}
