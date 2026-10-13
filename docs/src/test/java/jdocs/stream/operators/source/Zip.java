/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.source;

import java.util.List;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

public class Zip {

  void zipNSample() {
    ActorSystem system = null;

    // #zipN-simple
    Source<Object, NotUsed> chars = Source.from(List.of("a", "b", "c", "e", "f"));
    Source<Object, NotUsed> numbers = Source.from(List.of(1, 2, 3, 4, 5, 6));
    Source<Object, NotUsed> colors =
        Source.from(List.of("red", "green", "blue", "yellow", "purple"));

    Source.zipN(List.of(chars, numbers, colors)).runForeach(System.out::println, system);
    // prints:
    // [a, 1, red]
    // [b, 2, green]
    // [c, 3, blue]
    // [e, 4, yellow]
    // [f, 5, purple]

    // #zipN-simple
  }

  void zipWithNSample() {
    ActorSystem system = null;

    // #zipWithN-simple
    Source<Integer, NotUsed> numbers = Source.from(List.of(1, 2, 3, 4, 5, 6));
    Source<Integer, NotUsed> otherNumbers = Source.from(List.of(5, 2, 1, 4, 10, 4));
    Source<Integer, NotUsed> andSomeOtherNumbers = Source.from(List.of(3, 7, 2, 1, 1));

    Source.zipWithN(
            (List<Integer> seq) -> seq.stream().mapToInt(i -> i).max().getAsInt(),
            List.of(numbers, otherNumbers, andSomeOtherNumbers))
        .runForeach(System.out::println, system);
    // prints:
    // 5
    // 7
    // 3
    // 4
    // 10

    // #zipWithN-simple
  }

  void zipAllSample() {
    ActorSystem system = null;
    // #zipAll-simple

    Source<Integer, NotUsed> numbers = Source.from(List.of(1, 2, 3, 4));
    Source<String, NotUsed> letters = Source.from(List.of("a", "b", "c"));

    numbers.zipAll(letters, -1, "default").runForeach(System.out::println, system);
    // prints:
    // Pair(1,a)
    // Pair(2,b)
    // Pair(3,c)
    // Pair(4,default)
    // #zipAll-simple
  }
}
