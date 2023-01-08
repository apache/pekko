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

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.util.ByteString;
import org.junit.ClassRule;
import org.junit.Test;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;

public class FramingTest extends StreamTest {
  public FramingTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("FramingTest", PekkoSpec.testConf());

  @Test
  public void mustBeAbleToUseFraming() throws Exception {
    final Source<ByteString, NotUsed> in = Source.single(ByteString.fromString("1,3,4,5"));
    in.via(
            Framing.delimiter(
                ByteString.fromString(","), Integer.MAX_VALUE, FramingTruncation.ALLOW))
        .runWith(Sink.ignore(), system);
  }
}
