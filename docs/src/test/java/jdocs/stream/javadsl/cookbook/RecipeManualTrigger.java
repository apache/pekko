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

import java.time.Duration;
import java.util.Arrays;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.FanInShape2;
import org.apache.pekko.stream.FlowShape;
import org.apache.pekko.stream.SourceShape;
import org.apache.pekko.stream.javadsl.*;
import org.apache.pekko.stream.testkit.TestPublisher;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.stream.testkit.javadsl.TestSource;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecipeManualTrigger extends RecipeTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeManualTrigger");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  class Trigger {}

  public final Trigger TRIGGER = new Trigger();

  @Test
  public void zipped() throws Exception {
    new TestKit(system) {
      {
        final Source<Trigger, TestPublisher.Probe<Trigger>> triggerSource =
            TestSource.probe(system);
        final Sink<Message, TestSubscriber.Probe<Message>> messageSink = TestSink.probe(system);

        // #manually-triggered-stream
        final RunnableGraph<Pair<TestPublisher.Probe<Trigger>, TestSubscriber.Probe<Message>>> g =
            RunnableGraph
                .<Pair<TestPublisher.Probe<Trigger>, TestSubscriber.Probe<Message>>>fromGraph(
                    GraphDSL.create(
                        triggerSource,
                        messageSink,
                        (p, s) -> new Pair<>(p, s),
                        (builder, source, sink) -> {
                          SourceShape<Message> elements =
                              builder.add(
                                  Source.from(Arrays.asList("1", "2", "3", "4"))
                                      .map(t -> new Message(t)));
                          FlowShape<Pair<Message, Trigger>, Message> takeMessage =
                              builder.add(
                                  Flow.<Pair<Message, Trigger>>create().map(p -> p.first()));
                          final FanInShape2<Message, Trigger, Pair<Message, Trigger>> zip =
                              builder.add(Zip.create());
                          builder.from(elements).toInlet(zip.in0());
                          builder.from(source).toInlet(zip.in1());
                          builder.from(zip.out()).via(takeMessage).to(sink);
                          return ClosedShape.getInstance();
                        }));
        // #manually-triggered-stream

        Pair<TestPublisher.Probe<Trigger>, TestSubscriber.Probe<Message>> pubSub = g.run(system);
        TestPublisher.Probe<Trigger> pub = pubSub.first();
        TestSubscriber.Probe<Message> sub = pubSub.second();

        Duration timeout = Duration.ofMillis(100);
        sub.expectSubscription().request(1000);
        sub.expectNoMessage(timeout);

        pub.sendNext(TRIGGER);
        sub.expectNext(new Message("1"));
        sub.expectNoMessage(timeout);

        pub.sendNext(TRIGGER);
        pub.sendNext(TRIGGER);
        sub.expectNext(new Message("2"));
        sub.expectNext(new Message("3"));
        sub.expectNoMessage(timeout);

        pub.sendNext(TRIGGER);
        sub.expectNext(new Message("4"));
        sub.expectComplete();
      }
    };
  }

  @Test
  public void zipWith() throws Exception {
    new TestKit(system) {
      {
        final Source<Trigger, TestPublisher.Probe<Trigger>> triggerSource =
            TestSource.probe(system);
        final Sink<Message, TestSubscriber.Probe<Message>> messageSink = TestSink.probe(system);

        // #manually-triggered-stream-zipwith
        final RunnableGraph<Pair<TestPublisher.Probe<Trigger>, TestSubscriber.Probe<Message>>> g =
            RunnableGraph
                .<Pair<TestPublisher.Probe<Trigger>, TestSubscriber.Probe<Message>>>fromGraph(
                    GraphDSL.create(
                        triggerSource,
                        messageSink,
                        (p, s) -> new Pair<>(p, s),
                        (builder, source, sink) -> {
                          final SourceShape<Message> elements =
                              builder.add(
                                  Source.from(Arrays.asList("1", "2", "3", "4"))
                                      .map(t -> new Message(t)));
                          final FanInShape2<Message, Trigger, Message> zipWith =
                              builder.add(ZipWith.create((msg, trigger) -> msg));
                          builder.from(elements).toInlet(zipWith.in0());
                          builder.from(source).toInlet(zipWith.in1());
                          builder.from(zipWith.out()).to(sink);
                          return ClosedShape.getInstance();
                        }));
        // #manually-triggered-stream-zipwith

        Pair<TestPublisher.Probe<Trigger>, TestSubscriber.Probe<Message>> pubSub = g.run(system);
        TestPublisher.Probe<Trigger> pub = pubSub.first();
        TestSubscriber.Probe<Message> sub = pubSub.second();

        Duration timeout = Duration.ofMillis(100);
        sub.expectSubscription().request(1000);
        sub.expectNoMessage(timeout);

        pub.sendNext(TRIGGER);
        sub.expectNext(new Message("1"));
        sub.expectNoMessage(timeout);

        pub.sendNext(TRIGGER);
        pub.sendNext(TRIGGER);
        sub.expectNext(new Message("2"));
        sub.expectNext(new Message("3"));
        sub.expectNoMessage(timeout);

        pub.sendNext(TRIGGER);
        sub.expectNext(new Message("4"));
        sub.expectComplete();
      }
    };
  }
}
