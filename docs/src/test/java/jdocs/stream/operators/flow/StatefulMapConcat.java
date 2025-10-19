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

package jdocs.stream.operators.flow;

import java.util.*;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;

public class StatefulMapConcat {

  static final ActorSystem<?> system = null;

  static void zipWithIndex() {
    // #zip-with-index
    Source<Pair<String, Long>, NotUsed> letterAndIndex =
        Source.from(Arrays.asList("a", "b", "c", "d"))
            .statefulMapConcat(
                () -> {
                  // variables we close over with lambdas must be final, so we use a container,
                  // a 1 element array, for the actual value.
                  final long[] index = {0L};

                  // we return the function that will be invoked for each element
                  return (element) -> {
                    final Pair<String, Long> zipped = new Pair<>(element, index[0]);
                    index[0] += 1;
                    // we return an iterable with the single element
                    return Collections.singletonList(zipped);
                  };
                });

    letterAndIndex.runForeach(System.out::println, system);
    // prints
    // Pair(a,0)
    // Pair(b,1)
    // Pair(c,2)
    // Pair(d,3)
    // #zip-with-index
  }

  static void denylist() {
    // #denylist
    Source<String, NotUsed> fruitsAndDenyCommands =
        Source.from(
            Arrays.asList("banana", "pear", "orange", "deny:banana", "banana", "pear", "banana"));

    Flow<String, String, NotUsed> denyFilterFlow =
        Flow.of(String.class)
            .statefulMapConcat(
                () -> {
                  Set<String> denyList = new HashSet<>();

                  return (element) -> {
                    if (element.startsWith("deny:")) {
                      denyList.add(element.substring("deny:".length()));
                      return Collections
                          .emptyList(); // no element downstream when adding a deny listed keyword
                    } else if (denyList.contains(element)) {
                      return Collections
                          .emptyList(); // no element downstream if element is deny listed
                    } else {
                      return Collections.singletonList(element);
                    }
                  };
                });

    fruitsAndDenyCommands.via(denyFilterFlow).runForeach(System.out::println, system);
    // prints
    // banana
    // pear
    // orange
    // pear
    // #denylist
  }

  static void reactOnEnd() {
    // #bs-last
    Source<String, NotUsed> words =
        Source.from(Arrays.asList("baboon", "crocodile", "bat", "flamingo", "hedgehog", "beaver"));

    Flow<String, String, NotUsed> bWordsLast =
        Flow.of(String.class)
            .concat(Source.single("-end-"))
            .statefulMapConcat(
                () -> {
                  List<String> stashedBWords = new ArrayList<>();

                  return (element) -> {
                    if (element.startsWith("b")) {
                      // add to stash and emit no element
                      stashedBWords.add(element);
                      return Collections.emptyList();
                    } else if (element.equals("-end-")) {
                      // return in the stashed words in the order they got stashed
                      return stashedBWords;
                    } else {
                      // emit the element as is
                      return Collections.singletonList(element);
                    }
                  };
                });

    words.via(bWordsLast).runForeach(System.out::println, system);
    // prints
    // crocodile
    // flamingo
    // hedgehog
    // baboon
    // bat
    // beaver
    // #bs-last
  }
}
