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

package jdocs.future;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.dispatch.CompletionStages;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import jdocs.AbstractJavaTest;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.apache.pekko.actor.typed.javadsl.Adapter.toTyped;
// #imports

// #imports
import static java.util.concurrent.TimeUnit.SECONDS;

public class FutureDocTest extends AbstractJavaTest {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("FutureDocTest", PekkoSpec.testConf());

  private final ActorSystem<Void> system = toTyped(actorSystemResource.getSystem());

  @Test(expected = IllegalStateException.class)
  @SuppressWarnings("deprecation")
  public void useAfter() throws Exception {
    // #after
    CompletionStage<String> failWithException =
        CompletableFuture.supplyAsync(
            () -> {
              throw new IllegalStateException("OHNOES1");
            });
    CompletionStage<String> delayed =
        Patterns.after(Duration.ofMillis(200), system, () -> failWithException);
    // #after
    CompletionStage<String> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                Thread.sleep(1000);
              } catch (Throwable ex) {
                // ignore
              }
              return "foo";
            });
    CompletionStage<String> result =
        CompletionStages.firstCompletedOf(Arrays.asList(future, delayed));
    try {
      result.toCompletableFuture().get(2, SECONDS);
    } catch (Throwable ex) {
      throw (Exception) ex.getCause();
    }
  }

  @Test
  public void useRetry() throws Exception {
    // #retry
    Callable<CompletionStage<String>> attempt = () -> CompletableFuture.completedFuture("test");

    CompletionStage<String> retriedFuture =
        Patterns.retry(attempt, 3, java.time.Duration.ofMillis(200), system);
    // #retry

    retriedFuture.toCompletableFuture().get(2, SECONDS);
  }

  @Test
  public void useRetryWithPredicate() throws Exception {
    // #retry
    Callable<CompletionStage<String>> attempt = () -> CompletableFuture.completedFuture("test");

    CompletionStage<String> retriedFuture =
        Patterns.retry(
            attempt, (notUsed, e) -> e != null, 3, java.time.Duration.ofMillis(200), system);
    // #retry

    retriedFuture.toCompletableFuture().get(2, SECONDS);
  }

    @Test
    public void useRetryWithPredicateWithIntFunction() throws Exception {
        // #retry
        Callable<CompletionStage<String>> attempt = () -> CompletableFuture.completedFuture("test");

        CompletionStage<String> retriedFuture =
            Patterns.retry(
                attempt,
                (notUsed, e) -> e != null,
                3,
                current -> Optional.of(java.time.Duration.ofMillis(200)),
                system.classicSystem().scheduler(),
                system.executionContext());
        // #retry

        retriedFuture.toCompletableFuture().get(2, SECONDS);
    }
}
