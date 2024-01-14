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

package org.apache.pekko.actor.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.event.Logging;
import org.apache.pekko.japi.pf.PFBuilder;
import org.apache.pekko.testkit.CustomEventFilter;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ActorLoggingTest extends JUnitSuite {

  @ClassRule
  public static final TestKitJunitResource testKit =
      new TestKitJunitResource(
          ConfigFactory.parseString(
              "pekko.loglevel = INFO\n"
                  + "pekko.loggers = [\"org.apache.pekko.testkit.TestEventListener\"]"));

  interface Protocol {
    String getTransactionId();
  }

  static class Message implements Protocol {
    public final String transactionId;

    public Message(String transactionId) {
      this.transactionId = transactionId;
    }

    public String getTransactionId() {
      return transactionId;
    }
  }

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  @Test
  public void loggingProvidesClassWhereLogWasCalled() {
    CustomEventFilter eventFilter =
        new CustomEventFilter(
            PFBuilder.<Logging.LogEvent, Object>create()
                .match(
                    Logging.LogEvent.class, (event) -> event.logClass() == ActorLoggingTest.class)
                .build(),
            1);

    Behavior<String> behavior =
        Behaviors.setup(
            (context) -> {
              context.getLog().info("Starting up");

              return Behaviors.empty();
            });

    testKit.spawn(behavior);
    eventFilter.awaitDone(FiniteDuration.create(3, TimeUnit.SECONDS));
  }

  @Test
  public void loggingProvidesMDC() {
    Behavior<Protocol> behavior =
        Behaviors.setup(
            context ->
                Behaviors.withMdc(
                    Protocol.class,
                    (message) -> {
                      Map<String, String> mdc = new HashMap<>();
                      mdc.put("txId", message.getTransactionId());
                      return mdc;
                    },
                    Behaviors.receive(Protocol.class)
                        .onMessage(
                            Message.class,
                            message -> {
                              context.getLog().info(message.toString());
                              return Behaviors.same();
                            })
                        .build()));

    CustomEventFilter eventFilter =
        new CustomEventFilter(
            PFBuilder.<Logging.LogEvent, Object>create()
                .match(Logging.LogEvent.class, (event) -> event.getMDC().containsKey("txId"))
                .build(),
            1);

    testKit.spawn(behavior);
    eventFilter.awaitDone(FiniteDuration.create(3, TimeUnit.SECONDS));
  }

  @Test
  public void logMessagesBehavior() {
    Behavior<String> behavior = Behaviors.logMessages(Behaviors.empty());

    CustomEventFilter eventFilter =
        new CustomEventFilter(
            PFBuilder.<Logging.LogEvent, Object>create()
                .match(
                    Logging.LogEvent.class,
                    (event) -> event.message().equals("received message Hello"))
                .build(),
            1);

    ActorRef<String> ref = testKit.spawn(behavior);
    ref.tell("Hello");
    eventFilter.awaitDone(FiniteDuration.create(3, TimeUnit.SECONDS));
  }
}
