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

package jdocs.circuitbreaker;

// #imports1

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.event.LoggingAdapter;
import java.time.Duration;
import org.apache.pekko.pattern.CircuitBreaker;
import org.apache.pekko.event.Logging;

import static org.apache.pekko.pattern.Patterns.pipe;

import java.util.concurrent.CompletableFuture;

// #imports1

// #circuit-breaker-initialization
public class DangerousJavaActor extends AbstractActor {

  private final CircuitBreaker breaker;
  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  public DangerousJavaActor() {
    this.breaker =
        new CircuitBreaker(
                getContext().getDispatcher(),
                getContext().getSystem().getScheduler(),
                5,
                Duration.ofSeconds(10),
                Duration.ofMinutes(1))
            .addOnOpenListener(this::notifyMeOnOpen);
  }

  public void notifyMeOnOpen() {
    log.warning("My CircuitBreaker is now open, and will not close for one minute");
  }
  // #circuit-breaker-initialization

  // #circuit-breaker-usage
  public String dangerousCall() {
    return "This really isn't that dangerous of a call after all";
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            "is my middle name"::equals,
            m ->
                pipe(
                        breaker.callWithCircuitBreakerCS(
                            () -> CompletableFuture.supplyAsync(this::dangerousCall)),
                        getContext().getDispatcher())
                    .to(sender()))
        .match(
            String.class,
            "block for me"::equals,
            m -> {
              sender().tell(breaker.callWithSyncCircuitBreaker(this::dangerousCall), self());
            })
        .build();
  }
  // #circuit-breaker-usage

}
