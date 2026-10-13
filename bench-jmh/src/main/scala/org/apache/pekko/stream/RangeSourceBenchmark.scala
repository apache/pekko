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
import pekko.actor.ActorSystem
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.impl.fusing.IterableSource
import pekko.stream.scaladsl.{ Keep, RunnableGraph, Sink, Source }
import pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, InHandler }

object RangeSourceBenchmark {
  @volatile var sinkSum: Int = 0
}

final class IntCompletionLatch extends GraphStageWithMaterializedValue[SinkShape[Int], CountDownLatch] {
  val in: Inlet[Int] = Inlet[Int]("IntCompletionLatch.in")
  override val shape: SinkShape[Int] = SinkShape(in)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, CountDownLatch) = {
    val latch = new CountDownLatch(1)
    val logic = new GraphStageLogic(shape) with InHandler {
      private var sum = 0

      override def preStart(): Unit = pull(in)
      override def onPush(): Unit = {
        sum += grab(in)
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        RangeSourceBenchmark.sinkSum = sum
        latch.countDown()
        completeStage()
      }

      setHandler(in, this)
    }
    (logic, latch)
  }
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class RangeSourceBenchmark {
  implicit val system: ActorSystem = ActorSystem("RangeSourceBenchmark")

  @Param(Array("1", "1000"))
  var elements: Int = 0

  var rangeToLatch: RunnableGraph[CountDownLatch] = _
  var oldRangeToLatch: RunnableGraph[CountDownLatch] = _

  @Setup
  def setup(): Unit = {
    val range = 1 to elements
    rangeToLatch = Source(range).toMat(Sink.fromGraph(new IntCompletionLatch))(Keep.right)
    oldRangeToLatch = Source
      .fromGraph(new IterableSource[Int](range))
      .withAttributes(DefaultAttributes.iterableSource)
      .toMat(Sink.fromGraph(new IntCompletionLatch))(Keep.right)
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  @Benchmark
  def sourceRangeToLatch(): Unit =
    await(rangeToLatch.run())

  @Benchmark
  def oldSourceRangeToLatch(): Unit =
    await(oldRangeToLatch.run())

  private def await(latch: CountDownLatch): Unit =
    if (!latch.await(5, TimeUnit.SECONDS))
      throw new RuntimeException("Latch timed out")
}
