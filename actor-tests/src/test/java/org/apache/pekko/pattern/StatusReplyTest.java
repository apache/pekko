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
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.testkit.TestException;
import org.apache.pekko.testkit.TestProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class StatusReplyTest {

  @RegisterExtension
  static PekkoJUnitJupiterActorSystemResource actorSystemResource =
      new PekkoJUnitJupiterActorSystemResource("JavaAPI", PekkoSpec.testConf());

  @Test
  public void successReplyThrowsExceptionWhenGetErrorIsCalled() {
    StatusReply<String> reply = StatusReply.success("woho");
    assertTrue(reply.isSuccess());
    assertFalse(reply.isError());
    assertEquals("woho", reply.getValue());
    assertThrows(
        IllegalArgumentException.class,
        reply::getError,
        "Calling .getError() on success should throw");
  }

  @Test
  public void failedReplyThrowsExceptionWhenGetValueIsCalled() {
    StatusReply<String> reply = StatusReply.error("boho");
    assertTrue(reply.isError());
    assertFalse(reply.isSuccess());
    assertEquals("boho", reply.getError().getMessage());
    assertThrows(
        StatusReply.ErrorMessage.class,
        reply::getValue,
        "Calling .getValue() on error should throw");
  }

  @Test
  public void failedReplyThrowsOriginalExceptionWhenGetValueIsCalled() {
    StatusReply<String> reply = StatusReply.error(new TestException("boho"));
    assertTrue(reply.isError());
    assertFalse(reply.isSuccess());
    assertEquals("boho", reply.getError().getMessage());
    assertThrows(TestException.class, reply::getValue, "Calling .getValue() on error should throw");
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
        assertThrows(
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
        assertThrows(
            ExecutionException.class,
            () -> response.toCompletableFuture().get(3, TimeUnit.SECONDS));
    assertEquals(TestException.class, ex.getCause().getClass());
    assertEquals("boho", ex.getCause().getMessage());
  }
}
