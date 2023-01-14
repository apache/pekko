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

package jdocs.circuitbreaker;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.pattern.CircuitBreaker;
import java.time.Duration;

import java.util.Optional;
import java.util.function.BiFunction;

public class EvenNoFailureJavaExample extends AbstractActor {
  // #even-no-as-failure
  private final CircuitBreaker breaker;

  public EvenNoFailureJavaExample() {
    this.breaker =
        new CircuitBreaker(
            getContext().getDispatcher(),
            getContext().getSystem().getScheduler(),
            5,
            Duration.ofSeconds(10),
            Duration.ofMinutes(1));
  }

  public int luckyNumber() {
    BiFunction<Optional<Integer>, Optional<Throwable>, Boolean> evenNoAsFailure =
        (result, err) -> (result.isPresent() && result.get() % 2 == 0);

    // this will return 8888 and increase failure count at the same time
    return this.breaker.callWithSyncCircuitBreaker(() -> 8888, evenNoAsFailure);
  }

  // #even-no-as-failure
  @Override
  public Receive createReceive() {
    return null;
  }
}
