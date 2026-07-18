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

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.remote.artery.{ BenchTestSource, LatchSink }
import pekko.stream.scaladsl._

import com.typesafe.config.ConfigFactory

/**
 * Standalone benchmark runner for BroadcastHub consumer wheel performance.
 * Run with: sbt "bench-jmh/runMain org.apache.pekko.stream.BroadcastHubBenchRunner"
 */
object BroadcastHubBenchRunner {

  final val Elements = 100000
  final val SmallBuffer = 64
  final val LargeBuffer = 256
  final val WarmupRuns = 2
  final val MeasureRuns = 3

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseString("""
      pekko.actor.default-dispatcher {
        executor = "fork-join-executor"
        fork-join-executor {
          parallelism-factor = 1
        }
      }
    """)

    val consumerCounts = Array(64, 256, 1000, 2000)

    println("=" * 80)
    println("BroadcastHub Consumer Wheel Benchmark")
    println(s"Elements per run: $Elements")
    println(s"Warmup: $WarmupRuns runs, Measure: $MeasureRuns runs")
    println("=" * 80)

    for (bufferSize <- Array(SmallBuffer, LargeBuffer)) {
      println(s"\n--- Buffer size: $bufferSize (wheel slots: ${bufferSize * 2}) ---")
      println(f"${"Consumers"}%-12s ${"Avg (elem/s)"}%16s ${"Min"}%12s ${"Max"}%12s ${"StdDev"}%10s")
      println("-" * 70)

      for (consumerCount <- consumerCounts) {
        implicit val system: ActorSystem = ActorSystem(s"bench-$consumerCount-$bufferSize", config)

        // eager init
        SystemMaterializer(system).materializer

        val results = new Array[Double](WarmupRuns + MeasureRuns)

        for (run <- 0 until WarmupRuns + MeasureRuns) {
          val latch = new CountDownLatch(consumerCount)
          val broadcastSink =
            BroadcastHub.sink[java.lang.Integer](bufferSize = bufferSize, startAfterNrOfConsumers = consumerCount)
          val testSource = Source.fromGraph(new BenchTestSource(Elements))
          val source = testSource.runWith(broadcastSink)

          val start = System.nanoTime()
          var idx = 0
          while (idx < consumerCount) {
            source.runWith(new LatchSink(Elements, latch))
            idx += 1
          }

          if (!latch.await(120, TimeUnit.SECONDS)) {
            println(s"  TIMEOUT at consumers=$consumerCount buffer=$bufferSize run=$run")
            Await.result(system.terminate(), 10.seconds)
            System.exit(1)
          }
          val elapsed = (System.nanoTime() - start) / 1e9
          results(run) = Elements / elapsed
        }

        val measured = results.drop(WarmupRuns)
        val avg = measured.sum / measured.length
        val min = measured.min
        val max = measured.max
        val variance = measured.map(x => (x - avg) * (x - avg)).sum / measured.length
        val stddev = math.sqrt(variance)

        println(f"$consumerCount%-12d $avg%16.0f $min%12.0f $max%12.0f $stddev%10.0f")

        Await.result(system.terminate(), 10.seconds)
      }
    }

    println("\n" + "=" * 80)
    println("Done.")
  }
}
