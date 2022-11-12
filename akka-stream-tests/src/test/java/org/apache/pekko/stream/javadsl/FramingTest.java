/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.AkkaSpec;
import org.apache.pekko.util.ByteString;
import org.junit.ClassRule;
import org.junit.Test;
import org.apache.pekko.testkit.AkkaJUnitActorSystemResource;

public class FramingTest extends StreamTest {
  public FramingTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static AkkaJUnitActorSystemResource actorSystemResource =
      new AkkaJUnitActorSystemResource("FramingTest", AkkaSpec.testConf());

  @Test
  public void mustBeAbleToUseFraming() throws Exception {
    final Source<ByteString, NotUsed> in = Source.single(ByteString.fromString("1,3,4,5"));
    in.via(
            Framing.delimiter(
                ByteString.fromString(","), Integer.MAX_VALUE, FramingTruncation.ALLOW))
        .runWith(Sink.ignore(), system);
  }
}
