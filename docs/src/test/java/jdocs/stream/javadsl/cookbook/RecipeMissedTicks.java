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
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.testkit.TestPublisher;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.stream.testkit.javadsl.TestSource;
import org.apache.pekko.testkit.TestLatch;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.Await;

import java.util.concurrent.TimeUnit;

public class RecipeMissedTicks extends RecipeTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeMissedTicks");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void work() throws Exception {
    new TestKit(system) {
      class Tick {}

      final Tick Tick = new Tick();

      {
        final Source<Tick, TestPublisher.Probe<Tick>> tickStream = TestSource.probe(system);
        final Sink<Integer, TestSubscriber.Probe<Integer>> sink = TestSink.probe(system);

        @SuppressWarnings("unused")
        // #missed-ticks
        final Flow<Tick, Integer, NotUsed> missedTicks =
            Flow.of(Tick.class).conflateWithSeed(tick -> 0, (missed, tick) -> missed + 1);
        // #missed-ticks
        final TestLatch latch = new TestLatch(3, system);
        final Flow<Tick, Integer, NotUsed> realMissedTicks =
            Flow.of(Tick.class)
                .conflateWithSeed(
                    tick -> 0,
                    (missed, tick) -> {
                      latch.countDown();
                      return missed + 1;
                    });

        Pair<TestPublisher.Probe<Tick>, TestSubscriber.Probe<Integer>> pubSub =
            tickStream.via(realMissedTicks).toMat(sink, Keep.both()).run(system);
        TestPublisher.Probe<Tick> pub = pubSub.first();
        TestSubscriber.Probe<Integer> sub = pubSub.second();

        pub.sendNext(Tick);
        pub.sendNext(Tick);
        pub.sendNext(Tick);
        pub.sendNext(Tick);

        scala.concurrent.duration.FiniteDuration timeout =
            scala.concurrent.duration.FiniteDuration.create(200, TimeUnit.MILLISECONDS);

        Await.ready(latch, scala.concurrent.duration.Duration.create(1, TimeUnit.SECONDS));

        sub.request(1);
        sub.expectNext(3);
        sub.request(1);
        sub.expectNoMessage(timeout);

        pub.sendNext(Tick);
        sub.expectNext(0);

        pub.sendComplete();
        sub.request(1);
        sub.expectComplete();
      }
    };
  }
}
