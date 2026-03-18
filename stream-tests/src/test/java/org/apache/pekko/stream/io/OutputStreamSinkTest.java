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

package org.apache.pekko.stream.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.stream.IOOperationIncompleteException;
import org.apache.pekko.stream.IOResult;
import org.apache.pekko.stream.StreamTestJupiter;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamConverters;
import org.apache.pekko.stream.testkit.Utils;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OutputStreamSinkTest extends StreamTestJupiter {
  public OutputStreamSinkTest() {
    super(actorSystemResource);
  }

  @RegisterExtension
  static PekkoJUnitJupiterActorSystemResource actorSystemResource =
      new PekkoJUnitJupiterActorSystemResource(
          "OutputStreamSinkTest", Utils.UnboundedMailboxConfig());

  @Test
  public void mustSignalFailureViaFailingFuture() {

    final OutputStream os =
        new OutputStream() {
          volatile int left = 3;

          public void write(int data) {
            if (left == 0) {
              throw new RuntimeException("Can't accept more data.");
            }
            left -= 1;
          }
        };
    final CompletionStage<IOResult> resultFuture =
        Source.single(ByteString.fromString("123456"))
            .runWith(StreamConverters.fromOutputStream(() -> os), system);

    ExecutionException exception =
        Assertions.assertThrows(
            ExecutionException.class,
            () -> resultFuture.toCompletableFuture().get(3, TimeUnit.SECONDS),
            "CompletableFuture.get() should throw ExecutionException");
    assertEquals(
        IOOperationIncompleteException.class,
        exception.getCause().getClass(),
        "The cause of ExecutionException should be IOOperationIncompleteException");
    assertEquals("Can't accept more data.", exception.getCause().getCause().getMessage());
  }
}
