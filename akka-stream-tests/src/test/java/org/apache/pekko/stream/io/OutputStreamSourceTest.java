/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.io;

import static org.junit.Assert.assertEquals;

import java.io.OutputStream;
import java.time.Duration;

import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.ClassRule;
import org.junit.Test;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamConverters;
import org.apache.pekko.stream.testkit.Utils;
import org.apache.pekko.util.ByteString;

public class OutputStreamSourceTest extends StreamTest {
  public OutputStreamSourceTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("OutputStreamSourceTest", Utils.UnboundedMailboxConfig());

  @Test
  public void mustSendEventsViaOutputStream() throws Exception {
    final TestKit probe = new TestKit(system);
    final Duration timeout = Duration.ofSeconds(3);

    final Source<ByteString, OutputStream> source = StreamConverters.asOutputStream(timeout);
    final OutputStream s =
        source
            .to(
                Sink.foreach(
                    new Procedure<ByteString>() {
                      private static final long serialVersionUID = 1L;

                      public void apply(ByteString elem) {
                        probe.getRef().tell(elem, ActorRef.noSender());
                      }
                    }))
            .run(system);

    s.write("a".getBytes());

    assertEquals(ByteString.fromString("a"), probe.receiveOne(timeout));
    s.close();
  }
}
