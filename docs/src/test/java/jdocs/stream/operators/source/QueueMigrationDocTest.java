/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jdocs.stream.operators.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jdocs.AbstractJavaTest;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.stream.BoundedSourceQueue;
import org.apache.pekko.stream.BufferOverflowException;
import org.apache.pekko.stream.CompletionStrategy;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.QueueOfferResult;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.MergeHub;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class QueueMigrationDocTest extends AbstractJavaTest {

  static ActorSystem system;

  @BeforeAll
  public static void setup() {
    system = ActorSystem.create("QueueMigrationDocTest");
  }

  @AfterAll
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @SuppressWarnings("deprecation")
  private CompletionStage<QueueOfferResult> dropTailBefore() {
    // #drop-before
    SourceQueueWithComplete<Integer> queue =
        Source.<Integer>queue(100, OverflowStrategy.dropTail())
            .to(Sink.foreach(System.out::println))
            .run(system);

    CompletionStage<QueueOfferResult> result = queue.offer(1);
    // #drop-before
    return result;
  }

  @Test
  public void replaceDropTail() throws Exception {
    assertEquals(
        QueueOfferResult.enqueued(),
        dropTailBefore().toCompletableFuture().get(3, TimeUnit.SECONDS));

    // #drop-after
    BoundedSourceQueue<Integer> queue =
        Source.<Integer>queue(100).to(Sink.foreach(System.out::println)).run(system);

    // offer returns the result synchronously, there is no CompletionStage to wait for
    QueueOfferResult result = queue.offer(1);
    if (result == QueueOfferResult.enqueued()) {
      // element accepted
    } else if (result == QueueOfferResult.dropped()) {
      // buffer full, this element was dropped
    } else if (result instanceof QueueOfferResult.Failure) {
      // the stream failed
    } else if (result == QueueOfferResult.closed()) {
      // the stream was completed
    }
    // #drop-after
    assertEquals(QueueOfferResult.enqueued(), result);
  }

  // #fail-after
  static final int bufferSize = 2;

  // fail the stream like OverflowStrategy.fail did when the buffer is full
  static QueueOfferResult offerOrFail(BoundedSourceQueue<Integer> queue, int elem) {
    QueueOfferResult result = queue.offer(elem);
    if (result == QueueOfferResult.dropped()) {
      queue.fail(
          new BufferOverflowException("Buffer overflow (max capacity was: " + bufferSize + ")!"));
    }
    return result;
  }

  // #fail-after

  @Test
  public void replaceFail() {
    // downstream never requests, so the buffer fills up
    Pair<BoundedSourceQueue<Integer>, TestSubscriber.Probe<Integer>> pair =
        Source.<Integer>queue(bufferSize).toMat(TestSink.create(system), Keep.both()).run(system);
    BoundedSourceQueue<Integer> queue = pair.first();
    assertEquals(QueueOfferResult.enqueued(), offerOrFail(queue, 1));
    assertEquals(QueueOfferResult.enqueued(), offerOrFail(queue, 2));
    assertEquals(QueueOfferResult.dropped(), offerOrFail(queue, 3));
    assertInstanceOf(BufferOverflowException.class, pair.second().expectSubscriptionAndError());
  }

  @SuppressWarnings("deprecation")
  private CompletionStage<Done> backpressureBefore() {
    // #backpressure-before
    SourceQueueWithComplete<Integer> queue =
        Source.<Integer>queue(100, OverflowStrategy.backpressure())
            .to(Sink.foreach(System.out::println))
            .run(system);

    // the next offer is only made once the previous one has completed
    CompletionStage<QueueOfferResult> allOffered =
        CompletableFuture.completedFuture(QueueOfferResult.enqueued());
    for (int i = 1; i <= 10; i++) {
      final int elem = i;
      allOffered = allOffered.thenCompose(previous -> queue.offer(elem));
    }
    allOffered.thenRun(queue::complete);
    // #backpressure-before
    return queue.watchCompletion();
  }

  @Test
  public void replaceBackpressureSingleProducer() throws Exception {
    assertEquals(
        Done.getInstance(), backpressureBefore().toCompletableFuture().get(3, TimeUnit.SECONDS));

    // #backpressure-single-producer
    Pair<ActorRef, CompletionStage<List<Integer>>> pair =
        Source.<Integer>actorRefWithBackpressure(
                "ack",
                // complete when we send "complete"
                msg ->
                    "complete".equals(msg)
                        ? Optional.of(CompletionStrategy.draining())
                        : Optional.empty(),
                // do not fail on any message
                msg -> Optional.empty())
            .toMat(Sink.seq(), Keep.both())
            .run(system);
    ActorRef ref = pair.first();

    // Replaces queue.offer(elem): the CompletionStage completes with "ack" once the element was
    // emitted downstream. Unlike the deprecated queue it fails with an AskTimeoutException instead
    // of hanging forever if downstream stalls.
    Duration timeout = Duration.ofSeconds(3);

    // Like before, the next element must only be offered once the previous one was acknowledged,
    // otherwise the stream fails.
    CompletionStage<Object> allOffered = CompletableFuture.completedFuture("ack");
    for (int i = 1; i <= 10; i++) {
      final int elem = i;
      allOffered = allOffered.thenCompose(previous -> Patterns.ask(ref, elem, timeout));
    }
    allOffered.thenRun(() -> ref.tell("complete", ActorRef.noSender()));
    // #backpressure-single-producer

    assertEquals(
        IntStream.rangeClosed(1, 10).boxed().collect(Collectors.toList()),
        pair.second().toCompletableFuture().get(3, TimeUnit.SECONDS));
  }

  @Test
  public void replaceBackpressureMultipleProducers() throws Exception {
    // #backpressure-multiple-producers
    Pair<Sink<Integer, NotUsed>, CompletionStage<List<Integer>>> pair =
        MergeHub.of(Integer.class, 16).take(20).toMat(Sink.seq(), Keep.both()).run(system);
    Sink<Integer, NotUsed> sink = pair.first();

    // Each producer is a stream of its own and is back-pressured independently,
    // there is no offer CompletionStage to track per element.
    Source.range(1, 10).runWith(sink, system);
    Source.range(11, 20).runWith(sink, system);
    // #backpressure-multiple-producers

    List<Integer> result = pair.second().toCompletableFuture().get(3, TimeUnit.SECONDS);
    assertEquals(
        IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toSet()),
        result.stream().collect(Collectors.toSet()));
  }
}
