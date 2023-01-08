/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.source;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Arrays;
import java.util.stream.IntStream;

public class From {

  private ActorSystem system = null;

  void fromIteratorSample() {
    // #from-iterator
    Source.fromIterator(() -> Arrays.asList(1, 2, 3).iterator())
        .runForeach(System.out::println, system);
    // could print
    // 1
    // 2
    // 3
    // #from-iterator
  }

  void fromJavaStreamSample() {
    // #from-javaStream
    Source.fromJavaStream(() -> IntStream.rangeClosed(1, 3))
        .runForeach(System.out::println, system);
    // could print
    // 1
    // 2
    // 3
    // #from-javaStream
  }
}
