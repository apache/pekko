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

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.remote.artery.{ BenchTestSource, LatchSink }
import pekko.stream.scaladsl._
import pekko.stream.testkit.scaladsl.StreamTestKit

import com.typesafe.config.ConfigFactory

/**
 * Benchmarks BroadcastHub throughput under high-fan-out lockstep consumer scenarios.
 *
 * The consumer wheel uses a LongMap per slot for O(1) keyed add/remove without Long boxing.
 * In lockstep, all consumers cluster in the same wheel slot, maximizing per-slot contention.
 * With a small buffer (64), the wheel has only 128 slots, so `consumerCount / 128` consumers
 * share each slot — the old ArrayList.removeIf was O(k) per removal, now O(1).
 *
 * The `broadcast` benchmark parameterizes over consumer count with a fixed small buffer,
 * measuring how throughput scales as wheel slot pressure increases.
 *
 * The `broadcastLargeBuffer` benchmark uses a larger buffer (256) for comparison,
 * showing how the optimization holds up when consumers are spread across more slots.
 */
object BroadcastHubBenchmark {
  final val OperationsPerInvocation = 100000
  final val SmallBufferSize = 64
  final val LargeBufferSize = 256
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class BroadcastHubBenchmark {
  import BroadcastHubBenchmark._

  val config = ConfigFactory.parseString("""
    pekko.actor.default-dispatcher {
      executor = "fork-join-executor"
      fork-join-executor {
        parallelism-factor = 1
      }
    }
    """)

  implicit val system: ActorSystem = ActorSystem("BroadcastHubBenchmark", config)
  import system.dispatcher

  var testSource: Source[java.lang.Integer, NotUsed] = _

  @Param(Array("64", "256", "1000", "2000"))
  var parallelism = 0

  @Setup
  def setup(): Unit = {
    // eager init of materializer
    SystemMaterializer(system).materializer
    testSource = Source.fromGraph(new BenchTestSource(OperationsPerInvocation))
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  /**
   * Lockstep broadcast with small buffer (64).
   * All consumers stay at roughly the same wheel offset, clustering in the same slot.
   * With 128 wheel slots and 2000 consumers, ~16 consumers share each slot on average;
   * during NeedWakeup bursts, thousands cluster in a single slot.
   * This maximizes the O(1) vs O(k) per-removal difference.
   */
  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def broadcast(): Unit = {
    val latch = new CountDownLatch(parallelism)
    val broadcastSink =
      BroadcastHub.sink[java.lang.Integer](bufferSize = SmallBufferSize, startAfterNrOfConsumers = parallelism)
    val sink = new LatchSink(OperationsPerInvocation, latch)
    val source = testSource.runWith(broadcastSink)
    var idx = 0
    while (idx < parallelism) {
      source.runWith(sink)
      idx += 1
    }
    awaitLatch(latch)
  }

  /**
   * Lockstep broadcast with larger buffer (256) for comparison.
   * The wheel has 512 slots, so consumers are spread more thinly.
   * Shows how the optimization scales when per-slot pressure is lower.
   */
  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def broadcastLargeBuffer(): Unit = {
    val latch = new CountDownLatch(parallelism)
    val broadcastSink =
      BroadcastHub.sink[java.lang.Integer](bufferSize = LargeBufferSize, startAfterNrOfConsumers = parallelism)
    val sink = new LatchSink(OperationsPerInvocation, latch)
    val source = testSource.runWith(broadcastSink)
    var idx = 0
    while (idx < parallelism) {
      source.runWith(sink)
      idx += 1
    }
    awaitLatch(latch)
  }

  private def awaitLatch(latch: CountDownLatch): Unit = {
    if (!latch.await(60, TimeUnit.SECONDS)) {
      StreamTestKit.printDebugDump(SystemMaterializer(system).materializer.supervisor)
      throw new RuntimeException("Latch didn't complete in time")
    }
  }

}
