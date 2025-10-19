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

import java.util.Arrays;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

public class FlatMapMerge {
  private static ActorSystem system = null;

  // #flatmap-merge
  // e.g. could be a query to a database
  private Source<String, NotUsed> lookupCustomerEvents(String customerId) {
    return Source.from(Arrays.asList(customerId + "-evt-1", customerId + "-evt-2"));
  }
  // #flatmap-merge

  void example() {
    // #flatmap-merge
    Source.from(Arrays.asList("customer-1", "customer-2"))
        .flatMapMerge(10, this::lookupCustomerEvents)
        .runForeach(System.out::println, system);
    // prints - events from different customers could interleave
    // customer-1-evt-1
    // customer-2-evt-1
    // customer-1-evt-2
    // customer-2-evt-2
    // #flatmap-merge
  }
}
