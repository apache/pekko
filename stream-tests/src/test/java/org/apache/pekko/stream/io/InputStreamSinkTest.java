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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.pekko.stream.StreamTestJupiter;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamConverters;
import org.apache.pekko.stream.testkit.Utils;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class InputStreamSinkTest extends StreamTestJupiter {
  public InputStreamSinkTest() {
    super(actorSystemResource);
  }

  @RegisterExtension
  static PekkoJUnitJupiterActorSystemResource actorSystemResource =
      new PekkoJUnitJupiterActorSystemResource("InputStreamSink", Utils.UnboundedMailboxConfig());

  @Test
  public void mustReadEventViaInputStream() throws Exception {
    final Duration timeout = Duration.ofMillis(300);

    final Sink<ByteString, InputStream> sink = StreamConverters.asInputStream(timeout);
    final List<ByteString> list = Collections.singletonList(ByteString.fromString("a"));
    final InputStream stream = Source.from(list).runWith(sink, system);

    byte[] a = new byte[1];
    stream.read(a);
    assertTrue(Arrays.equals("a".getBytes(), a));
  }
}
