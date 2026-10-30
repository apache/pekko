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

package org.apache.pekko.stream

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.impl.ResizableMultiReaderRingBuffer
import pekko.stream.impl.ResizableMultiReaderRingBuffer.{ Cursor, Cursors }
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

import org.reactivestreams.{ Subscriber, Subscription }

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class FanoutRingBufferBenchmark {
  @Param(Array("1", "2", "8", "32", "128"))
  var subscribers = 0

  private var readers: List[Cursor] = _
  private var buffer: ResizableMultiReaderRingBuffer[Int] = _

  @Setup
  def setup(): Unit = {
    readers = List.fill(subscribers)(new Cursor { var cursor = 0 })
    buffer = new ResizableMultiReaderRingBuffer[Int](16, 16, new Cursors { def cursors = readers })
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def publish(): Int = {
    var sum = 0
    var i = 0
    while (i < 1024) {
      if (!buffer.write(1)) throw new IllegalStateException("Buffer did not drain")
      var remaining = readers
      while (remaining.nonEmpty) {
        sum += buffer.read(remaining.head)
        remaining = remaining.tail
      }
      i += 1
    }
    sum
  }
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class FanoutPublisherBenchmark {
  @Param(Array("1", "2", "8", "32", "128"))
  var subscribers = 0

  @Param(Array("1", "16", "9223372036854775807"))
  var requestBatchSize = 0L

  private implicit var system: ActorSystem = _

  @Setup
  def setup(): Unit = system = ActorSystem("FanoutPublisherBenchmark")

  @Benchmark
  @OperationsPerInvocation(100000)
  def publish(): Unit = {
    val subscribed = new CountDownLatch(subscribers)
    val completed = new CountDownLatch(subscribers)
    val failure = new AtomicReference[Throwable]()
    val (start, publisher) = Source
      .maybe[Int]
      .concat(Source.repeat(1).take(100000))
      .toMat(Sink.asPublisher[Int](true))(Keep.both)
      .run()

    for (_ <- 1 to subscribers) {
      publisher.subscribe(new Subscriber[Int] {
        private var received = 0
        private var subscription: Subscription = _
        private var remaining = requestBatchSize
        override def onSubscribe(subscription: Subscription): Unit = {
          this.subscription = subscription
          subscription.request(requestBatchSize)
          subscribed.countDown()
        }
        override def onNext(element: Int): Unit = {
          received += element
          if (requestBatchSize != Long.MaxValue) {
            remaining -= 1
            if (remaining == 0 && received < 100000) {
              remaining = requestBatchSize
              subscription.request(requestBatchSize)
            }
          }
        }
        override def onError(cause: Throwable): Unit = {
          failure.compareAndSet(null, cause)
          completed.countDown()
        }
        override def onComplete(): Unit = {
          if (received != 100000)
            failure.compareAndSet(null, new IllegalStateException(s"Expected 100000 elements, received $received"))
          completed.countDown()
        }
      })
    }
    if (!subscribed.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Subscription timed out")
    start.success(None)
    if (!completed.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Completion timed out")
    if (failure.get() ne null) throw new IllegalStateException("Publisher failed", failure.get())
  }

  @TearDown
  def shutdown(): Unit = Await.result(system.terminate(), 5.seconds)
}
