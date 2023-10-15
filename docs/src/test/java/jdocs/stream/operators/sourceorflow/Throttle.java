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

import docs.stream.operators.sourceorflow.ThrottleCommon.Frame;
import java.time.Duration;
import java.util.stream.Stream;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.ThrottleMode;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

/** */
public class Throttle {

  public static void main(String[] args) {
    ActorSystem actorSystem = ActorSystem.create("25fps-throttled-stream");
    Materializer mat = Materializer.matFromSystem(actorSystem);

    Source<Frame, NotUsed> frameSource =
        Source.fromIterator(() -> Stream.iterate(0, i -> i + 1).iterator())
            .map(i -> new Frame(i.intValue()));

    // #throttle
    int framesPerSecond = 24;

    Source<Frame, NotUsed> videoThrottling =
        frameSource.throttle(framesPerSecond, Duration.ofSeconds(1));
    // serialize `Frame` and send over the network.
    // #throttle

    // #throttle-with-burst
    Source<Frame, NotUsed> throttlingWithBurst =
        frameSource.throttle(
            framesPerSecond, Duration.ofSeconds(1), framesPerSecond * 30, ThrottleMode.shaping());
    // serialize `Frame` and send over the network.
    // #throttle-with-burst

    videoThrottling.map(f -> f.i()).to(Sink.foreach(System.out::println)).run(mat);
    throttlingWithBurst.take(1000L).map(f -> f.i()).to(Sink.foreach(System.out::println)).run(mat);
  }
}
