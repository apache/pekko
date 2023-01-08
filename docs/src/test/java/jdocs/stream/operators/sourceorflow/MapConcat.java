/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.sourceorflow;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Arrays;

public class MapConcat {
  private static ActorSystem system = null;

  // #map-concat
  Iterable<Integer> duplicate(int i) {
    return Arrays.asList(i, i);
  }

  // #map-concat

  void example() {
    // #map-concat
    Source.from(Arrays.asList(1, 2, 3))
        .mapConcat(i -> duplicate(i))
        .runForeach(System.out::println, system);
    // prints:
    // 1
    // 1
    // 2
    // 2
    // 3
    // 3
    // #map-concat
  }
}
