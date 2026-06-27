/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.sourceorflow;

import java.util.List;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

public class Intersperse {
  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create();
    // #intersperse
    Source.from(List.of(1, 2, 3))
        .map(String::valueOf)
        .intersperse("[", ", ", "]")
        .runForeach(System.out::print, system);
    // prints
    // [1, 2, 3]
    // #intersperse
    system.terminate();
  }
}
