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

package jdocs.stream;

import static org.apache.pekko.stream.javadsl.AsPublisher.*;
// #asPublisher-import

import java.util.stream.Stream;
import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.*;

// #asPublisher-import

public class MigrationsJava {

  public static void main(String[] args) {
    // #expand-continually
    Flow.of(Integer.class).expand(in -> Stream.iterate(in, i -> i).iterator());
    // #expand-continually
    // #expand-state
    Flow.of(Integer.class)
        .expand(
            in ->
                Stream.iterate(new Pair<>(in, 0), p -> new Pair<>(in, p.second() + 1)).iterator());
    // #expand-state

    // #asPublisher
    Sink.asPublisher(WITH_FANOUT); // instead of Sink.asPublisher(true)
    Sink.asPublisher(WITHOUT_FANOUT); // instead of Sink.asPublisher(false)
    // #asPublisher

    // #async
    Flow<Integer, Integer, NotUsed> flow = Flow.of(Integer.class).map(n -> n + 1);
    Source.range(1, 10).via(flow.async());
    // #async
  }
}
