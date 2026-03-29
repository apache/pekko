/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.flow;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.GatherCollector;
import org.apache.pekko.stream.javadsl.Gatherer;
import org.apache.pekko.stream.javadsl.Source;

public class Gather {
  static final ActorSystem system = null;

  public void zipWithIndex() {
    // #zipWithIndex
    Source.from(Arrays.asList("A", "B", "C", "D"))
        .gather(
            () ->
                new Gatherer<String, String>() {
                  private long index = 0L;

                  @Override
                  public void apply(String elem, GatherCollector<String> collector) {
                    collector.push("(" + elem + "," + index + ")");
                    index += 1;
                  }
                })
        .runForeach(System.out::println, system);
    // prints
    // (A,0)
    // (B,1)
    // (C,2)
    // (D,3)
    // #zipWithIndex
  }

  public void grouped() {
    // #grouped
    Source.from(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        .gather(
            () ->
                new Gatherer<Integer, String>() {
                  private final ArrayList<Integer> buffer = new ArrayList<>(3);

                  @Override
                  public void apply(Integer elem, GatherCollector<String> collector) {
                    buffer.add(elem);
                    if (buffer.size() == 3) {
                      collector.push(buffer.toString());
                      buffer.clear();
                    }
                  }

                  @Override
                  public void onComplete(GatherCollector<String> collector) {
                    if (!buffer.isEmpty()) {
                      collector.push(buffer.toString());
                    }
                  }
                })
        .runForeach(System.out::println, system);
    // prints
    // [1, 2, 3]
    // [4, 5, 6]
    // [7, 8, 9]
    // [10]
    // #grouped
  }
}
