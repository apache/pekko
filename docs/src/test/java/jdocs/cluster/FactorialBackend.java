/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.AbstractActor;
import static org.apache.pekko.pattern.Patterns.pipe;

// #backend
public class FactorialBackend extends AbstractActor {

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            n -> {
              CompletableFuture<FactorialResult> result =
                  CompletableFuture.supplyAsync(() -> factorial(n))
                      .thenApply((factorial) -> new FactorialResult(n, factorial));

              pipe(result, getContext().dispatcher()).to(getSender());
            })
        .build();
  }

  BigInteger factorial(int n) {
    BigInteger acc = BigInteger.ONE;
    for (int i = 1; i <= n; ++i) {
      acc = acc.multiply(BigInteger.valueOf(i));
    }
    return acc;
  }
}
// #backend
