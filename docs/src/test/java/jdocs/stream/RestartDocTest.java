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

package jdocs.stream;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.KillSwitch;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.RestartSettings;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.RestartSource;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RestartDocTest {

  static ActorSystem system;
  static Materializer materializer;

  // Mocking pekko-http
  public static class Http {
    public static Http get(ActorSystem system) {
      return new Http();
    }

    public CompletionStage<Http> singleRequest(String uri) {
      return new CompletableFuture<>();
    }

    public NotUsed entity() {
      return NotUsed.getInstance();
    }
  }

  public static class HttpRequest {
    public static String create(String uri) {
      return uri;
    }
  }

  public static class ServerSentEvent {}

  public static class EventStreamUnmarshalling {
    public static EventStreamUnmarshalling fromEventStream() {
      return new EventStreamUnmarshalling();
    }

    public CompletionStage<Source<ServerSentEvent, NotUsed>> unmarshall(
        Http http, Materializer mat) {
      return new CompletableFuture<>();
    }
  }

  public void doSomethingElse() {}

  public void recoverWithBackoffSource() {
    // #restart-with-backoff-source
    RestartSettings settings =
        RestartSettings.create(
                Duration.ofSeconds(3), // min backoff
                Duration.ofSeconds(30), // max backoff
                0.2 // adds 20% "noise" to vary the intervals slightly
                )
            .withMaxRestarts(
                20, Duration.ofMinutes(5)); // limits the amount of restarts to 20 within 5 minutes

    Source<ServerSentEvent, NotUsed> eventStream =
        RestartSource.withBackoff(
            settings,
            () ->
                // Create a source from a future of a source
                Source.completionStageSource(
                    // Issue a GET request on the event stream
                    Http.get(system)
                        .singleRequest(HttpRequest.create("http://example.com/eventstream"))
                        .thenCompose(
                            response ->
                                // Unmarshall it to a stream of ServerSentEvents
                                EventStreamUnmarshalling.fromEventStream()
                                    .unmarshall(response, materializer))));
    // #restart-with-backoff-source

    // #with-kill-switch
    KillSwitch killSwitch =
        eventStream
            .viaMat(KillSwitches.single(), Keep.right())
            .toMat(Sink.foreach(event -> System.out.println("Got event: " + event)), Keep.left())
            .run(materializer);

    doSomethingElse();

    killSwitch.shutdown();
    // #with-kill-switch

  }
}
