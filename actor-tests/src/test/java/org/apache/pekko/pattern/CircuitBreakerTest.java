/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.pattern;

import org.apache.pekko.actor.*;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.util.FutureConverters;
import org.apache.pekko.util.JavaDurationConverters;
import org.junit.ClassRule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import scala.concurrent.Await;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class CircuitBreakerTest extends JUnitSuite {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("JavaAPI", PekkoSpec.testConf());

  private final ActorSystem system = actorSystemResource.getSystem();

  @Test
  public void useCircuitBreakerWithCompletableFuture() throws Exception {
    final Duration fiveSeconds = Duration.ofSeconds(5);
    final Duration fiveHundredMillis = Duration.ofMillis(500);
    final CircuitBreaker breaker =
        new CircuitBreaker(
            system.dispatcher(), system.scheduler(), 1, fiveSeconds, fiveHundredMillis);

    final CompletableFuture<String> f = new CompletableFuture<>();
    f.complete("hello");
    final CompletionStage<String> res = breaker.callWithCircuitBreakerCS(() -> f);
    assertEquals(
        "hello",
        Await.result(
            FutureConverters.asScala(res), JavaDurationConverters.asFiniteDuration(fiveSeconds)));
  }

  @Test
  public void useCircuitBreakerWithCompletableFutureAndCustomDefineFailure() throws Exception {
    final Duration fiveSeconds = Duration.ofSeconds(5);
    final Duration fiveHundredMillis = Duration.ofMillis(500);
    final CircuitBreaker breaker =
        new CircuitBreaker(
            system.dispatcher(), system.scheduler(), 1, fiveSeconds, fiveHundredMillis);

    final BiFunction<Optional<String>, Optional<Throwable>, java.lang.Boolean> fn =
        (result, err) -> (result.isPresent() && result.get().equals("hello"));

    final CompletableFuture<String> f = new CompletableFuture<>();
    f.complete("hello");
    final CompletionStage<String> res = breaker.callWithCircuitBreakerCS(() -> f, fn);
    assertEquals(
        "hello",
        Await.result(
            FutureConverters.asScala(res), JavaDurationConverters.asFiniteDuration(fiveSeconds)));
    assertEquals(1, breaker.currentFailureCount());
  }
}
