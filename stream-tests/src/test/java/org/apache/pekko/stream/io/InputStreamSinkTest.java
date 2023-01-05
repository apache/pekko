/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.io;

import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamConverters;
import org.apache.pekko.stream.testkit.Utils;
import org.apache.pekko.util.ByteString;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class InputStreamSinkTest extends StreamTest {
  public InputStreamSinkTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("InputStreamSink", Utils.UnboundedMailboxConfig());

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
