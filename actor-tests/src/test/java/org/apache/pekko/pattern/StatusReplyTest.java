/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.pattern;

import static org.apache.pekko.pattern.Patterns.askWithStatus;
import static org.junit.Assert.*;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.testkit.TestException;
import org.apache.pekko.testkit.TestProbe;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class StatusReplyTest extends JUnitSuite {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("JavaAPI", PekkoSpec.testConf());

  @Test
  public void successReplyThrowsExceptionWhenGetErrorIsCalled() {
    StatusReply<String> reply = StatusReply.success("woho");
    assertTrue(reply.isSuccess());
    assertFalse(reply.isError());
    assertEquals("woho", reply.getValue());
    Assert.assertThrows(
        "Calling .getError() on success should throw",
        IllegalArgumentException.class,
        reply::getError);
  }

  @Test
  public void failedReplyThrowsExceptionWhenGetValueIsCalled() {
    StatusReply<String> reply = StatusReply.error("boho");
    assertTrue(reply.isError());
    assertFalse(reply.isSuccess());
    assertEquals("boho", reply.getError().getMessage());
    Assert.assertThrows(
        "Calling .getValue() on error should throw",
        StatusReply.ErrorMessage.class,
        reply::getValue);
  }

  @Test
  public void failedReplyThrowsOriginalExceptionWhenGetValueIsCalled() {
    StatusReply<String> reply = StatusReply.error(new TestException("boho"));
    assertTrue(reply.isError());
    assertFalse(reply.isSuccess());
    assertEquals("boho", reply.getError().getMessage());
    Assert.assertThrows(
        "Calling .getValue() on error should throw", TestException.class, reply::getValue);
  }

  @Test
  public void askWithStatusSuccessReturnsValue() throws Exception {
    TestProbe probe = new TestProbe(actorSystemResource.getSystem());

    CompletionStage<Object> response = askWithStatus(probe.ref(), "request", Duration.ofSeconds(3));
    probe.expectMsg("request");
    probe.lastSender().tell(StatusReply.success("woho"), ActorRef.noSender());

    Object result = response.toCompletableFuture().get(3, TimeUnit.SECONDS);
    assertEquals("woho", result);
  }

  @Test
  public void askWithStatusErrorReturnsErrorMessageExceptionForText() {
    TestProbe probe = new TestProbe(actorSystemResource.getSystem());

    CompletionStage<Object> response = askWithStatus(probe.ref(), "request", Duration.ofSeconds(3));
    probe.expectMsg("request");
    probe.lastSender().tell(StatusReply.error("boho"), ActorRef.noSender());
    ExecutionException ex =
        Assert.assertThrows(
            ExecutionException.class,
            () -> response.toCompletableFuture().get(3, TimeUnit.SECONDS));
    assertEquals(StatusReply.ErrorMessage.class, ex.getCause().getClass());
    assertEquals("boho", ex.getCause().getMessage());
  }

  @Test
  public void askWithStatusErrorReturnsOriginalException() {
    TestProbe probe = new TestProbe(actorSystemResource.getSystem());

    CompletionStage<Object> response = askWithStatus(probe.ref(), "request", Duration.ofSeconds(3));
    probe.expectMsg("request");
    probe.lastSender().tell(StatusReply.error(new TestException("boho")), ActorRef.noSender());
    ExecutionException ex =
        Assert.assertThrows(
            ExecutionException.class,
            () -> response.toCompletableFuture().get(3, TimeUnit.SECONDS));
    assertEquals(TestException.class, ex.getCause().getClass());
    assertEquals("boho", ex.getCause().getMessage());
  }
}
