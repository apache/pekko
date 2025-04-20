/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BufferUntilChangedTest {
    private static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create("BufferUntilChangedTest");
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void shouldBufferElementsUntilTheyChange() {
        TestSubscriber.Probe<List<String>> probe =
                Source.from(Arrays.asList("A", "B", "B", "C", "C", "C", "D"))
                        .via(BufferUntilChanged.flow())
                        .runWith(TestSink.probe(system), system);

        probe.request(4);
        probe.expectNext(Collections.singletonList("A"));
        probe.expectNext(Arrays.asList("B", "B"));
        probe.expectNext(Arrays.asList("C", "C", "C"));
        probe.expectNext(Collections.singletonList("D"));
        probe.expectComplete();
    }

    @Test
    public void shouldBufferElementsUntilTheyChangeWithKeySelector() {
        TestSubscriber.Probe<List<Integer>> probe =
                Source.from(Arrays.asList(1, 2, 3, 13, 4, 14))
                        .via(BufferUntilChanged.flow(i -> i % 10))
                        .runWith(TestSink.probe(system), system);

        probe.request(4);
        probe.expectNext(Collections.singletonList(1));
        probe.expectNext(Collections.singletonList(2));
        probe.expectNext(Arrays.asList(3, 13));
        probe.expectNext(Arrays.asList(4, 14));
        probe.expectComplete();
    }

    @Test
    public void shouldBufferElementsUntilTheyChangeWithKeySelectorAndComparator() {
        TestSubscriber.Probe<List<Integer>> probe =
                Source.from(Arrays.asList(1, 3, 5, 2, 4, 6))
                        .via(BufferUntilChanged.flow(i -> i, (a, b) -> (a % 2) == (b % 2)))
                        .runWith(TestSink.probe(system), system);

        probe.request(2);
        probe.expectNext(Arrays.asList(1, 3, 5));
        probe.expectNext(Arrays.asList(2, 4, 6));
        probe.expectComplete();
    }

    @Test
    public void shouldWorkWithEmptySources() {
        TestSubscriber.Probe<List<String>> probe =
                Source.<String>empty()
                        .via(BufferUntilChanged.flow())
                        .runWith(TestSink.probe(system), system);

        probe.request(1);
        probe.expectComplete();
    }

    @Test
    public void shouldWorkWithSingleElementSources() {
        TestSubscriber.Probe<List<String>> probe =
                Source.single("A")
                        .via(BufferUntilChanged.flow())
                        .runWith(TestSink.probe(system), system);

        probe.request(1);
        probe.expectNext(Collections.singletonList("A"));
        probe.expectComplete();
    }

    @Test
    public void shouldWorkWithSourceFactory() {
        TestSubscriber.Probe<List<String>> probe =
                BufferUntilChanged.source(Source.from(Arrays.asList("A", "B", "B", "C", "C", "C", "D")))
                        .runWith(TestSink.probe(system), system);

        probe.request(4);
        probe.expectNext(Collections.singletonList("A"));
        probe.expectNext(Arrays.asList("B", "B"));
        probe.expectNext(Arrays.asList("C", "C", "C"));
        probe.expectNext(Collections.singletonList("D"));
        probe.expectComplete();
    }
}
