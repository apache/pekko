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

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.Futures;
import org.apache.pekko.japi.pf.PFBuilder;
import org.apache.pekko.stream.BackpressureTimeoutException;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Promise;

public class RecipeAdhocSourceTest extends RecipeTest {
  static ActorSystem system;
  Duration duration200mills = Duration.ofMillis(200);

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeAdhocSource");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  // #adhoc-source
  public <T> Source<T, ?> adhocSource(Source<T, ?> source, Duration timeout, int maxRetries) {
    return Source.lazySource(
        () ->
            source
                .backpressureTimeout(timeout)
                .recoverWithRetries(
                    maxRetries,
                    PFBuilder.<Throwable, Source<T, NotUsed>>create()
                        .match(
                            TimeoutException.class,
                            ex ->
                                Source.lazySource(() -> source.backpressureTimeout(timeout))
                                    .mapMaterializedValue(v -> NotUsed.getInstance()))
                        .build()));
  }

  // #adhoc-source

  @Test
  @Ignore
  public void noStart() throws Exception {
    new TestKit(system) {
      {
        AtomicBoolean isStarted = new AtomicBoolean();
        adhocSource(
            Source.empty()
                .mapMaterializedValue(
                    x -> {
                      isStarted.set(true);
                      return x;
                    }),
            duration200mills,
            3);
        Thread.sleep(300);
        assertEquals(false, isStarted.get());
      }
    };
  }

  @Test
  @Ignore
  public void startStream() throws Exception {
    new TestKit(system) {
      {
        TestSubscriber.Probe<String> probe =
            adhocSource(Source.repeat("a"), duration200mills, 3)
                .toMat(TestSink.probe(system), Keep.right())
                .run(system);
        probe.requestNext("a");
      }
    };
  }

  @Test
  @Ignore
  public void shutdownStream() throws Exception {
    new TestKit(system) {
      {
        Promise<Done> shutdown = Futures.promise();
        TestSubscriber.Probe<String> probe =
            adhocSource(
                    Source.repeat("a")
                        .watchTermination(
                            (a, term) -> term.thenRun(() -> shutdown.success(Done.getInstance()))),
                    duration200mills,
                    3)
                .toMat(TestSink.probe(system), Keep.right())
                .run(system);

        probe.requestNext("a");
        Thread.sleep(300);
        Await.result(shutdown.future(), duration("3 seconds"));
      }
    };
  }

  @Test
  @Ignore
  public void notShutDownStream() throws Exception {
    new TestKit(system) {
      {
        Promise<Done> shutdown = Futures.promise();
        TestSubscriber.Probe<String> probe =
            adhocSource(
                    Source.repeat("a")
                        .watchTermination(
                            (a, term) -> term.thenRun(() -> shutdown.success(Done.getInstance()))),
                    duration200mills,
                    3)
                .toMat(TestSink.probe(system), Keep.right())
                .run(system);

        probe.requestNext("a");
        Thread.sleep(100);
        probe.requestNext("a");
        Thread.sleep(100);
        probe.requestNext("a");
        Thread.sleep(100);
        probe.requestNext("a");
        Thread.sleep(100);
        probe.requestNext("a");
        Thread.sleep(100);

        assertEquals(false, shutdown.isCompleted());
      }
    };
  }

  @Test
  @Ignore
  public void restartUponDemand() throws Exception {
    new TestKit(system) {
      {
        Promise<Done> shutdown = Futures.promise();
        AtomicInteger startedCount = new AtomicInteger(0);

        Source<String, ?> source =
            Source.<String>empty()
                .mapMaterializedValue(x -> startedCount.incrementAndGet())
                .concat(Source.repeat("a"));

        TestSubscriber.Probe<String> probe =
            adhocSource(
                    source.watchTermination(
                        (a, term) -> term.thenRun(() -> shutdown.success(Done.getInstance()))),
                    duration200mills,
                    3)
                .toMat(TestSink.probe(system), Keep.right())
                .run(system);

        probe.requestNext("a");
        assertEquals(1, startedCount.get());
        Thread.sleep(200);
        Await.result(shutdown.future(), duration("3 seconds"));
      }
    };
  }

  @Test
  @Ignore
  public void restartUptoMaxRetries() throws Exception {
    new TestKit(system) {
      {
        Promise<Done> shutdown = Futures.promise();
        AtomicInteger startedCount = new AtomicInteger(0);

        Source<String, ?> source =
            Source.<String>empty()
                .mapMaterializedValue(x -> startedCount.incrementAndGet())
                .concat(Source.repeat("a"));

        TestSubscriber.Probe<String> probe =
            adhocSource(
                    source.watchTermination(
                        (a, term) -> term.thenRun(() -> shutdown.success(Done.getInstance()))),
                    duration200mills,
                    3)
                .toMat(TestSink.probe(system), Keep.right())
                .run(system);

        probe.requestNext("a");
        assertEquals(1, startedCount.get());

        Thread.sleep(500);
        assertEquals(true, shutdown.isCompleted());

        Thread.sleep(500);
        probe.requestNext("a");
        assertEquals(2, startedCount.get());

        Thread.sleep(500);
        probe.requestNext("a");
        assertEquals(3, startedCount.get());

        Thread.sleep(500);
        probe.requestNext("a");
        assertEquals(4, startedCount.get()); // startCount == 4, which means "re"-tried 3 times

        Thread.sleep(500);
        assertEquals(BackpressureTimeoutException.class, probe.expectError().getClass());
        probe.request(1); // send demand
        probe.expectNoMessage(Duration.ofMillis(200)); // but no more restart
      }
    };
  }
}
