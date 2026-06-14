/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.pekko.stream

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.annotations.OperationsPerInvocation
import org.openjdk.jmh.infra.Blackhole

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.RunnableGraph
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import com.typesafe.config.ConfigFactory

object ActorGraphInterpreterBoundaryBenchmark {
  final val ElementCount = 100 * 1000
  final val CancelAfter = ElementCount / 2

  final class SynchronousPublisher(elements: Array[MutableElement]) extends Publisher[MutableElement] {
    override def subscribe(subscriber: Subscriber[? >: MutableElement]): Unit = {
      if (subscriber eq null) throw new NullPointerException("subscriber")

      subscriber.onSubscribe(new Subscription {
        private var cancelled = false
        private var index = 0

        override def request(n: Long): Unit =
          if (!cancelled) {
            if (n <= 0) {
              cancelled = true
              subscriber.onError(new IllegalArgumentException("non-positive subscription request"))
            } else {
              var remaining = n
              while (remaining > 0 && index < elements.length && !cancelled) {
                subscriber.onNext(elements(index))
                index += 1
                remaining -= 1
              }
              if (index == elements.length && !cancelled) {
                cancelled = true
                subscriber.onComplete()
              }
            }
          }

        override def cancel(): Unit = cancelled = true
      })
    }
  }

  final class RequestOneSubscriber(
      blackhole: Blackhole,
      latch: CountDownLatch,
      cancelAfter: Int)
      extends Subscriber[MutableElement] {
    private var subscription: Subscription = _
    private var seen = 0
    private val failure = new AtomicReference[Throwable]

    override def onSubscribe(subscription: Subscription): Unit = {
      this.subscription = subscription
      subscription.request(1)
    }

    override def onNext(element: MutableElement): Unit = {
      blackhole.consume(element.value)
      seen += 1
      if (cancelAfter > 0 && seen == cancelAfter) {
        subscription.cancel()
        latch.countDown()
      } else {
        subscription.request(1)
      }
    }

    override def onError(cause: Throwable): Unit = {
      failure.set(cause)
      latch.countDown()
    }

    override def onComplete(): Unit = latch.countDown()

    def awaitExpected(expected: Int): Unit = {
      if (!latch.await(10, TimeUnit.SECONDS))
        throw new RuntimeException("Latch timed out")

      val cause = failure.get()
      if (cause ne null) throw new RuntimeException("Subscriber failed", cause)
      if (seen < expected) throw new RuntimeException(s"Expected at least [$expected] elements but got [$seen]")
    }
  }
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class ActorGraphInterpreterBoundaryBenchmark {
  import ActorGraphInterpreterBoundaryBenchmark._

  implicit val system: ActorSystem = ActorSystem(
    "test",
    ConfigFactory.parseString(s"""
      pekko.stream.materializer.sync-processing-limit = ${Int.MaxValue}
    """))

  private var publisherSource: RunnableGraph[CountDownLatch] = _
  private var publisherSink: RunnableGraph[Publisher[MutableElement]] = _

  @Setup
  def setup(): Unit = {
    SystemMaterializer(system).materializer
    val testElements = Array.fill(ElementCount)(new MutableElement(1))
    val testSource = Source.fromGraph(new TestSource(testElements))

    publisherSource =
      Source
        .fromPublisher(new SynchronousPublisher(testElements))
        .toMat(Sink.fromGraph(new JitSafeCompletionLatch))(Keep.right)
    publisherSink =
      testSource.toMat(Sink.asPublisher(false))(Keep.right)
  }

  @Benchmark
  @OperationsPerInvocation(ElementCount)
  def external_publisher_to_sink(blackhole: Blackhole): Unit = {
    FusedGraphsBenchmark.blackhole = blackhole
    val latch = publisherSource.run()
    if (!latch.await(10, TimeUnit.SECONDS))
      throw new RuntimeException("Latch timed out")
  }

  @Benchmark
  @OperationsPerInvocation(ElementCount)
  def source_to_external_subscriber_request_one(blackhole: Blackhole): Unit = {
    val latch = new CountDownLatch(1)
    val subscriber = new RequestOneSubscriber(blackhole, latch, cancelAfter = 0)

    publisherSink.run().subscribe(subscriber)
    subscriber.awaitExpected(ElementCount)
  }

  @Benchmark
  @OperationsPerInvocation(CancelAfter)
  def source_to_external_subscriber_cancel_halfway(blackhole: Blackhole): Unit = {
    val latch = new CountDownLatch(1)
    val subscriber = new RequestOneSubscriber(blackhole, latch, cancelAfter = CancelAfter)

    publisherSink.run().subscribe(subscriber)
    subscriber.awaitExpected(CancelAfter)
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }
}
