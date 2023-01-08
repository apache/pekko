/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.javadsl.cookbook;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Compression;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.ByteString;
import static org.apache.pekko.util.ByteString.emptyByteString;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class RecipeDecompress extends RecipeTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeDecompress");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void parseLines() throws Exception {
    final Source<ByteString, NotUsed> dataStream =
        Source.single(ByteString.fromString("Hello World"));

    final Source<ByteString, NotUsed> compressedStream = dataStream.via(Compression.gzip());

    // #decompress-gzip
    final Source<ByteString, NotUsed> decompressedStream =
        compressedStream.via(Compression.gunzip(100));
    // #decompress-gzip

    ByteString decompressedData =
        decompressedStream
            .runFold(emptyByteString(), ByteString::concat, system)
            .toCompletableFuture()
            .get(1, TimeUnit.SECONDS);
    String decompressedString = decompressedData.utf8String();
    Assert.assertEquals("Hello World", decompressedString);
  }
}
