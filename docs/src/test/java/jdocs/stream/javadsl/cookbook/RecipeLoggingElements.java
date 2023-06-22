/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.javadsl.cookbook;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.DebugFilter;
import org.apache.pekko.testkit.javadsl.TestKit;
import com.typesafe.config.ConfigFactory;
import jdocs.stream.SilenceSystemOut;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.runtime.AbstractFunction0;

import java.util.Arrays;

public class RecipeLoggingElements extends RecipeTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system =
        ActorSystem.create(
            "RecipeLoggingElements",
            ConfigFactory.parseString(
                "pekko.loglevel=DEBUG\npekko.loggers = [org.apache.pekko.testkit.TestEventListener]"));
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void workWithPrintln() throws Exception {
    new TestKit(system) {
      final SilenceSystemOut.System System = SilenceSystemOut.get(getTestActor());

      {
        final Source<String, NotUsed> mySource = Source.from(Arrays.asList("1", "2", "3"));

        // #println-debug
        mySource.map(
            elem -> {
              System.out.println(elem);
              return elem;
            });
        // #println-debug
      }
    };
  }

  @Test
  public void workWithLog() throws Exception {
    new TestKit(system) {
      private <T> T analyse(T i) {
        return i;
      }

      {
        final Source<String, NotUsed> mySource = Source.from(Arrays.asList("1", "2", "3"));

        // #log-custom
        // customise log levels
        mySource
            .log("before-map")
            .withAttributes(
                Attributes.createLogLevels(
                    Logging.WarningLevel(), // onElement
                    Logging.InfoLevel(), // onFinish
                    Logging.DebugLevel() // onFailure
                    ))
            .map(i -> analyse(i));

        // or provide custom logging adapter
        final LoggingAdapter adapter = Logging.getLogger(system, "customLogger");
        mySource.log("custom", adapter);
        // #log-custom

        new DebugFilter("customLogger", "[custom] Element: ", false, false, 3)
            .intercept(
                new AbstractFunction0<Object>() {
                  public Void apply() {
                    mySource.log("custom", adapter).runWith(Sink.ignore(), system);
                    return null;
                  }
                },
                system);
      }
    };
  }

  @Test
  public void errorLog() throws Exception {
    new TestKit(system) {
      {
        // #log-error
        Source.from(Arrays.asList(-1, 0, 1))
            .map(x -> 1 / x) // throwing ArithmeticException: / by zero
            .log("error logging")
            .runWith(Sink.ignore(), system);
        // #log-error
      }
    };
  }
}
