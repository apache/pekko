/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.sourceorflow;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.FlowMonitor;
import org.apache.pekko.stream.FlowMonitorState;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

/** */
public class Monitor {

  // #monitor
  private static <T> void printMonitorState(FlowMonitorState.StreamState<T> state) {
    if (state == FlowMonitorState.finished()) {
      System.out.println("Stream is initialized but hasn't processed any element");
    } else if (state instanceof FlowMonitorState.Received) {
      FlowMonitorState.Received msg = (FlowMonitorState.Received) state;
      System.out.println("Last message received: " + msg.msg());
    } else if (state instanceof FlowMonitorState.Failed) {
      Throwable cause = ((FlowMonitorState.Failed) state).cause();
      System.out.println("Stream failed with cause: " + cause.getMessage());
    } else {
      System.out.println("Stream completed already");
    }
  }
  // #monitor

  public static void main(String[] args)
      throws InterruptedException, TimeoutException, ExecutionException {
    ActorSystem actorSystem = ActorSystem.create("25fps-stream");

    // #monitor
    Source<Integer, FlowMonitor<Integer>> monitoredSource =
        Source.fromIterator(() -> Arrays.asList(0, 1, 2, 3, 4, 5).iterator())
            .throttle(5, Duration.ofSeconds(1))
            .monitorMat(Keep.right());

    Pair<FlowMonitor<Integer>, CompletionStage<Done>> run =
        monitoredSource.toMat(Sink.foreach(System.out::println), Keep.both()).run(actorSystem);

    FlowMonitor<Integer> monitor = run.first();

    // If we peek in the monitor too early, it's possible it was not initialized yet.
    printMonitorState(monitor.state());

    // Periodically check the monitor
    Source.tick(Duration.ofMillis(200), Duration.ofMillis(400), "")
        .runForeach(__ -> printMonitorState(monitor.state()), actorSystem);
    // #monitor

    run.second().toCompletableFuture().whenComplete((x, t) -> actorSystem.terminate());
  }
}
