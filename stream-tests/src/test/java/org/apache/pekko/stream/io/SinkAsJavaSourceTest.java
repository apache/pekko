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

package org.apache.pekko.stream.io;

import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamConverters;
import org.apache.pekko.stream.testkit.Utils;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class SinkAsJavaSourceTest extends StreamTest {
  public SinkAsJavaSourceTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("SinkAsJavaSourceTest", Utils.UnboundedMailboxConfig());

  @Test
  public void mustBeAbleToUseAsJavaStream() throws Exception {
    final List<Integer> list = Arrays.asList(1, 2, 3);
    final Sink<Integer, Stream<Integer>> streamSink = StreamConverters.asJavaStream();
    java.util.stream.Stream<Integer> javaStream = Source.from(list).runWith(streamSink, system);
    assertEquals(list, javaStream.collect(Collectors.toList()));
  }
}
