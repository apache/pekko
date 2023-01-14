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

package jdocs.stream.operators.sourceorflow;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Arrays;

public class MergeLatest {

  private static final ActorSystem<Void> system = null;

  public static void example() {
    // #mergeLatest
    Source<Integer, NotUsed> prices = Source.from(Arrays.asList(100, 101, 99, 103));
    Source<Integer, NotUsed> quantities = Source.from(Arrays.asList(1, 3, 4, 2));

    prices
        .mergeLatest(quantities, true)
        .map(priceAndQuantity -> priceAndQuantity.get(0) * priceAndQuantity.get(1))
        .runForeach(System.out::println, system);

    // prints something like:
    // 100
    // 101
    // 303
    // 297
    // 396
    // 412
    // 206
    // #mergeLatest
  }
}
