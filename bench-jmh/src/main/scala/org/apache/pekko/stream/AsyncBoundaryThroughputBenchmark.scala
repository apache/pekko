/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.scaladsl._
import pekko.stream.stage._

import com.typesafe.config.ConfigFactory

object AsyncBoundaryThroughputBenchmark {
  final val ElementCount = 100 * 1000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class AsyncBoundaryThroughputBenchmark {

  import AsyncBoundaryThroughputBenchmark._

  val config = ConfigFactory.parseString(s"""
      pekko.stream.materializer.sync-processing-limit = ${Int.MaxValue}
    """)

  implicit val system: ActorSystem = ActorSystem("AsyncBoundaryThroughputBenchmark", config)

  @Param(Array("1", "3", "10"))
  var asyncBoundaries = 0

  var source: Source[Int, NotUsed] = _
  var flow: Flow[Int, Int, NotUsed] = _

  @Setup
  def setup(): Unit = {
    SystemMaterializer(system).materializer
    source = Source(1 to ElementCount)
    var f: Flow[Int, Int, NotUsed] = Flow[Int]
    for (_ <- 1 to asyncBoundaries) {
      f = f.map(identity).async
    }
    flow = f
  }

  @Benchmark
  @OperationsPerInvocation(ElementCount)
  def async_boundary_throughput(blackhole: Blackhole): CountDownLatch = {
    FusedGraphsBenchmark.blackhole = blackhole
    val latch = source
      .via(flow)
      .toMat(Sink.fromGraph(new JitSafeCompletionLatchInt))(Keep.right)
      .run()
    if (!latch.await(30, TimeUnit.SECONDS))
      throw new RuntimeException("Latch timed out")
    latch
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }
}

class JitSafeCompletionLatchInt extends GraphStageWithMaterializedValue[SinkShape[Int], CountDownLatch] {
  val in = Inlet[Int]("JitSafeCompletionLatchInt.in")
  override val shape = SinkShape(in)

  @nowarn("cat=unused-params")
  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, CountDownLatch) = {
    val latch = new CountDownLatch(1)
    val logic = new GraphStageLogic(shape) with InHandler {
      private var count = 0

      override def preStart(): Unit = pull(in)
      override def onPush(): Unit = {
        grab(in) // consume element
        count += 1
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        FusedGraphsBenchmark.blackhole.consume(count)
        latch.countDown()
        completeStage()
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        latch.countDown()
        throw ex
      }

      setHandler(in, this)
    }
    (logic, latch)
  }
}
