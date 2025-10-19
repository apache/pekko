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

public class FlatMapConcat {
  private static ActorSystem system = null;

  // #flatmap-concat
  // e.g. could be a query to a database
  private Source<String, NotUsed> lookupCustomerEvents(String customerId) {
    return Source.from(Arrays.asList(customerId + "-event-1", customerId + "-event-2"));
  }
  // #flatmap-concat

  void example() {
    // #flatmap-concat
    Source.from(Arrays.asList("customer-1", "customer-2"))
        .flatMapConcat(this::lookupCustomerEvents)
        .runForeach(System.out::println, system);
    // prints - events from each customer consecutively
    // customer-1-event-1
    // customer-1-event-2
    // customer-2-event-1
    // customer-2-event-2
    // #flatmap-concat
  }
}
