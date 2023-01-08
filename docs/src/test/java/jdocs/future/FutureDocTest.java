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

package jdocs.future;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.dispatch.Futures;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.util.Timeout;
import jdocs.AbstractJavaTest;
import org.junit.ClassRule;
import org.junit.Test;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.apache.pekko.actor.typed.javadsl.Adapter.toTyped;
import static org.apache.pekko.dispatch.Futures.future;
// #imports

// #imports
import static java.util.concurrent.TimeUnit.SECONDS;

public class FutureDocTest extends AbstractJavaTest {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("FutureDocTest", PekkoSpec.testConf());

  private final ActorSystem<Void> system = toTyped(actorSystemResource.getSystem());

  @Test(expected = java.util.concurrent.CompletionException.class)
  public void useAfter() throws Exception {
    final ExecutionContext ec = system.executionContext();
    // #after
    CompletionStage<String> failWithException =
        CompletableFuture.supplyAsync(
            () -> {
              throw new IllegalStateException("OHNOES1");
            });
    CompletionStage<String> delayed =
        Patterns.after(Duration.ofMillis(200), system, () -> failWithException);
    // #after
    Future<String> future =
        future(
            () -> {
              Thread.sleep(1000);
              return "foo";
            },
            ec);
    Future<String> result =
        Futures.firstCompletedOf(
            Arrays.<Future<String>>asList(future, FutureConverters.toScala(delayed)), ec);
    Timeout timeout = Timeout.create(Duration.ofSeconds(2));
    Await.result(result, timeout.duration());
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
}
