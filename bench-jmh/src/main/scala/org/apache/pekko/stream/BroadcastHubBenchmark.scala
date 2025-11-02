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

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.remote.artery.{ BenchTestSource, LatchSink }
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.testkit.scaladsl.StreamTestKit

import com.typesafe.config.ConfigFactory

object BroadcastHubBenchmark {
  final val OperationsPerInvocation = 100000
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

  @Param(Array("64", "256"))
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

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def broadcast(): Unit = {
    val latch = new CountDownLatch(parallelism)
    val broadcastSink =
      BroadcastHub.sink[java.lang.Integer](bufferSize = parallelism, startAfterNrOfConsumers = parallelism)
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
    if (!latch.await(30, TimeUnit.SECONDS)) {
      StreamTestKit.printDebugDump(SystemMaterializer(system).materializer.supervisor)
      throw new RuntimeException("Latch didn't complete in time")
    }
  }

}
