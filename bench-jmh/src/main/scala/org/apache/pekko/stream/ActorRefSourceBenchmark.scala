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
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko

import org.openjdk.jmh.annotations._

import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

object ActorRefSourceBenchmark {
  final val OperationsPerInvocation = 100000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class ActorRefSourceBenchmark {
  import ActorRefSourceBenchmark._

  implicit val system: ActorSystem = ActorSystem("ActorRefSourceBenchmark")

  @Setup
  def setup(): Unit = {
    SystemMaterializer(system).materializer
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def actorRef_source_no_buffer_100k(): Unit = {
    val doneLatch = new CountDownLatch(1)
    val sourceRef = Source
      .actorRef[Any](
        completionMatcher = { case "done" => CompletionStrategy.draining },
        failureMatcher = PartialFunction.empty,
        bufferSize = 0,
        overflowStrategy = OverflowStrategy.dropHead)
      .toMat(Sink.ignore)(Keep.left)
      .run()

    val sender = new Thread(() => {
      var i = 0
      while (i < OperationsPerInvocation) {
        sourceRef ! i
        i += 1
      }
      sourceRef ! "done"
      doneLatch.countDown()
    })
    sender.start()
    if (!doneLatch.await(30, TimeUnit.SECONDS))
      throw new RuntimeException("ActorRefSource benchmark timed out")
    sender.join()
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def actorRef_source_pingpong_100k(): Unit = {
    val counter = new AtomicLong(0)
    val sourceRef = Source
      .actorRef[Long](
        completionMatcher = { case -1L => CompletionStrategy.draining },
        failureMatcher = PartialFunction.empty,
        bufferSize = 1,
        overflowStrategy = OverflowStrategy.dropHead)
      .toMat(Sink.foreach[Long](_ => counter.incrementAndGet()))(Keep.left)
      .run()

    var i = 0L
    while (i < OperationsPerInvocation) {
      sourceRef ! i
      val expected = i + 1
      while (counter.get() < expected) () // spin-wait for consumption
      i += 1
    }
    sourceRef ! -1L
  }
}
