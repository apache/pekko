/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch;

import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.actor.ActorSystem;

import org.apache.pekko.japi.*;
import org.junit.ClassRule;
import org.scalatestplus.junit.JUnitSuite;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.apache.pekko.japi.Util.classTag;

import org.apache.pekko.testkit.PekkoSpec;

@SuppressWarnings("deprecation")
public class JavaFutureTests extends JUnitSuite {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("JavaFutureTests", PekkoSpec.testConf());

  private final ActorSystem system = actorSystemResource.getSystem();
  private final Duration timeout = Duration.create(5, TimeUnit.SECONDS);

  @Test
  public void mustBeAbleToCreateAJavaCompletionStage() throws Exception {
    Future<Integer> f = Futures.successful(42);
    CompletableFuture<Integer> cs = Futures.asJava(f).toCompletableFuture();
    assertEquals(42, cs.get(3, TimeUnit.SECONDS).intValue());
  }

  @Test
  public void blockMustBeCallable() throws Exception {
    Promise<String> p = Futures.promise();
    Duration d = Duration.create(1, TimeUnit.SECONDS);
    p.success("foo");
    Await.ready(p.future(), d);
    assertEquals("foo", Await.result(p.future(), d));
  }

  @Test
  public void mapToMustBeCallable() throws Exception {
    Promise<Object> p = Futures.promise();
    Future<String> f = p.future().mapTo(classTag(String.class));
    Duration d = Duration.create(1, TimeUnit.SECONDS);
    p.success("foo");
    Await.ready(p.future(), d);
    assertEquals("foo", Await.result(p.future(), d));
  }

}
