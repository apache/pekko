/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream;

import org.apache.pekko.Done;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.scaladsl.TcpAttributes;
import org.apache.pekko.testkit.AkkaJUnitActorSystemResource;
import org.apache.pekko.testkit.AkkaSpec;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class StreamAttributeDocTest extends StreamTest {

  public StreamAttributeDocTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static AkkaJUnitActorSystemResource actorSystemResource =
      new AkkaJUnitActorSystemResource(
          "StreamAttributeDocTest",
          ConfigFactory.parseString("my-stream-dispatcher = akka.test.stream-dispatcher")
              .withFallback(AkkaSpec.testConf()));

  @Test
  public void runnableAttributesExample() throws Exception {
    final PrintStream oldOut = System.out;
    // no stdout from tests thank you
    System.setOut(new PrintStream(new ByteArrayOutputStream()));
    try {

      // #attributes-on-stream
      RunnableGraph<CompletionStage<Done>> stream =
          Source.range(1, 10)
              .map(Object::toString)
              .toMat(Sink.foreach(System.out::println), Keep.right())
              .withAttributes(
                  Attributes.inputBuffer(4, 4)
                      .and(ActorAttributes.dispatcher("my-stream-dispatcher"))
                      .and(TcpAttributes.tcpWriteBufferSize(2048)));

      // #attributes-on-stream
      CompletionStage<Done> done =
          // #attributes-on-stream
          stream.run(system);
      // #attributes-on-stream

      done.toCompletableFuture().get(3, TimeUnit.SECONDS);
    } finally {
      System.setOut(oldOut);
    }
  }
}
