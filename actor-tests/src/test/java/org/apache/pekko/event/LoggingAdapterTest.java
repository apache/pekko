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

package org.apache.pekko.event;

import static org.apache.pekko.event.Logging.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.*;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.ActorWithMDC.Log;
import org.apache.pekko.event.Logging.Error;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class LoggingAdapterTest extends JUnitSuite {

  private static final Config config = ConfigFactory.parseString("pekko.loglevel = DEBUG\n");

  @Rule
  public PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("LoggingAdapterTest", config);

  private ActorSystem system = null;

  @Before
  public void beforeEach() {
    system = actorSystemResource.getSystem();
  }

  @Test
  public void mustFormatMessage() {
    final LoggingAdapter log = Logging.getLogger(system, this);
    new LogJavaTestKit(system) {
      {
        system.getEventStream().subscribe(getRef(), LogEvent.class);
        log.error("One arg message: {}", "the arg");
        expectLog(ErrorLevel(), "One arg message: the arg");

        Throwable cause =
            new IllegalStateException("This state is illegal") {
              @Override
              public synchronized Throwable fillInStackTrace() {
                return this; // no stack trace
              }
            };
        log.error(cause, "Two args message: {}, {}", "an arg", "another arg");
        expectLog(ErrorLevel(), "Two args message: an arg, another arg", cause);

        int[] primitiveArgs = {10, 20, 30};
        log.warning("Args as array of primitives: {}, {}, {}", primitiveArgs);
        expectLog(WarningLevel(), "Args as array of primitives: 10, 20, 30");

        Date now = new Date();
        UUID uuid = UUID.randomUUID();
        Object[] objArgs = {"A String", now, uuid};
        log.info("Args as array of objects: {}, {}, {}", objArgs);
        expectLog(
            InfoLevel(),
            "Args as array of objects: A String, " + now.toString() + ", " + uuid.toString());

        log.debug("Four args message: {}, {}, {}, {}", 1, 2, 3, 4);
        expectLog(DebugLevel(), "Four args message: 1, 2, 3, 4");
      }
    };
  }

  @Test
  public void mustCallMdcForEveryLog() throws Exception {
    new LogJavaTestKit(system) {
      {
        system.getEventStream().subscribe(getRef(), LogEvent.class);
        ActorRef ref = system.actorOf(Props.create(ActorWithMDC.class));

        ref.tell(new Log(ErrorLevel(), "An Error"), system.deadLetters());
        expectLog(ErrorLevel(), "An Error", "{messageLength=8}");
        ref.tell(new Log(WarningLevel(), "A Warning"), system.deadLetters());
        expectLog(WarningLevel(), "A Warning", "{messageLength=9}");
        ref.tell(new Log(InfoLevel(), "Some Info"), system.deadLetters());
        expectLog(InfoLevel(), "Some Info", "{messageLength=9}");
        ref.tell(new Log(DebugLevel(), "No MDC for 4th call"), system.deadLetters());
        expectLog(DebugLevel(), "No MDC for 4th call");
        ref.tell(
            new Log(Logging.DebugLevel(), "And now yes, a debug with MDC"), system.deadLetters());
        expectLog(DebugLevel(), "And now yes, a debug with MDC", "{messageLength=29}");
      }
    };
  }

  @Test
  public void mustSupportMdcNull() throws Exception {
    new LogJavaTestKit(system) {
      {
        system.getEventStream().subscribe(getRef(), LogEvent.class);
        ActorRef ref = system.actorOf(Props.create(ActorWithMDC.class));

        ref.tell(new Log(InfoLevel(), "Null MDC"), system.deadLetters());
        expectLog(InfoLevel(), "Null MDC", "{}");
      }
    };
  }

  /*
   * #3671: Let the application specify custom MDC values
   * Java backward compatibility check
   */
  @Test
  public void mustBeAbleToCreateLogEventsWithOldConstructor() throws Exception {
    assertNotNull(new Error(new Exception(), "logSource", LoggingAdapterTest.class, "The message"));
    assertNotNull(new Error("logSource", LoggingAdapterTest.class, "The message"));
    assertNotNull(new Warning("logSource", LoggingAdapterTest.class, "The message"));
    assertNotNull(new Info("logSource", LoggingAdapterTest.class, "The message"));
    assertNotNull(new Debug("logSource", LoggingAdapterTest.class, "The message"));
  }

  private static class LogJavaTestKit extends TestKit {

    private static final String emptyMDC = "{}";

    public LogJavaTestKit(ActorSystem system) {
      super(system);
    }

    void expectLog(Object level, String message) {
      expectLog(level, message, null, emptyMDC);
    }

    void expectLog(Object level, String message, Throwable cause) {
      expectLog(level, message, cause, emptyMDC);
    }

    void expectLog(Object level, String message, String mdc) {
      expectLog(level, message, null, mdc);
    }

    void expectLog(
        final Object level, final String message, final Throwable cause, final String mdc) {
      expectMsgPF(
          Duration.ofSeconds(3),
          "LogEvent",
          event -> {
            LogEvent log = (LogEvent) event;
            assertEquals(message, log.message());
            assertEquals(level, log.level());
            assertEquals(mdc, log.getMDC().toString());
            if (cause != null) {
              assertTrue(event instanceof LogEventWithCause);
              LogEventWithCause causedEvent = (LogEventWithCause) event;
              assertSame(cause, causedEvent.cause());
            }
            return null;
          });
    }
  }
}
