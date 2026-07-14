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

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko

import org.openjdk.jmh.annotations._

import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.scaladsl._

object MaterializerWiringBenchmark {

  val linearFlowBuilder: Int => RunnableGraph[NotUsed] = numOfOperators => {
    var source = Source.single(())
    for (_ <- 1 to numOfOperators) {
      source = source.map(identity)
    }
    source.to(Sink.ignore)
  }

  val broadcastMergeBuilder: Int => RunnableGraph[NotUsed] = numOfJunctions =>
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val broadcast = b.add(Broadcast[Unit](numOfJunctions))
      var outlet = broadcast.out(0)
      for (i <- 1 until numOfJunctions) {
        val merge = b.add(Merge[Unit](2))
        outlet           ~> merge
        broadcast.out(i) ~> merge
        outlet = merge.out
      }

      Source.single(()) ~> broadcast
      outlet            ~> Sink.ignore
      ClosedShape
    })

  val broadcastMergeImmediateBuilder: Int => RunnableGraph[NotUsed] = numOfJunctions =>
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val broadcast = b.add(Broadcast[Unit](numOfJunctions))
      val merge = b.add(Merge[Unit](numOfJunctions))
      for (_ <- 0 until numOfJunctions) {
        broadcast ~> merge
      }

      Source.single(()) ~> broadcast
      merge             ~> Sink.ignore
      ClosedShape
    })

  val importedFlowBuilder: Int => RunnableGraph[NotUsed] = numOfFlows =>
    RunnableGraph.fromGraph(GraphDSL.createGraph(Source.single(())) { implicit b => source =>
      import GraphDSL.Implicits._
      val flow = Flow[Unit].map(identity)
      var out: Outlet[Unit] = source.out
      for (_ <- 0 until numOfFlows) {
        val flowShape = b.add(flow)
        out ~> flowShape
        out = flowShape.outlet
      }
      out ~> Sink.ignore
      ClosedShape
    })
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class MaterializerWiringBenchmark {

  import MaterializerWiringBenchmark._

  implicit val system: ActorSystem = ActorSystem("MaterializerWiringBenchmark")

  var linearFlow: RunnableGraph[NotUsed] = _
  var broadcastMerge: RunnableGraph[NotUsed] = _
  var broadcastMergeImmediate: RunnableGraph[NotUsed] = _
  var importedFlow: RunnableGraph[NotUsed] = _

  @Param(Array("100", "500", "1000"))
  var complexity = 0

  @Setup
  def setup(): Unit = {
    SystemMaterializer(system).materializer
    linearFlow = linearFlowBuilder(complexity)
    broadcastMerge = broadcastMergeBuilder(complexity)
    broadcastMergeImmediate = broadcastMergeImmediateBuilder(complexity)
    importedFlow = importedFlowBuilder(complexity)
  }

  @Benchmark
  def linear(): NotUsed = linearFlow.run()

  @Benchmark
  def broadcast_merge_gradual(): NotUsed = broadcastMerge.run()

  @Benchmark
  def broadcast_merge_immediate(): NotUsed = broadcastMergeImmediate.run()

  @Benchmark
  def imported_flow(): NotUsed = importedFlow.run()

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }
}
