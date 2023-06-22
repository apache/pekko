/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.scaladsl.RunnableGraph

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class GraphBuilderBenchmark {

  @Param(Array("1", "10", "100", "1000"))
  var complexity = 0

  @Benchmark
  def flow_with_map(): RunnableGraph[NotUsed] =
    MaterializationBenchmark.flowWithMapBuilder(complexity)

  @Benchmark
  def graph_with_junctions_gradual(): RunnableGraph[NotUsed] =
    MaterializationBenchmark.graphWithJunctionsGradualBuilder(complexity)

  @Benchmark
  def graph_with_junctions_immediate(): RunnableGraph[NotUsed] =
    MaterializationBenchmark.graphWithJunctionsImmediateBuilder(complexity)

  @Benchmark
  def graph_with_imported_flow(): RunnableGraph[NotUsed] =
    MaterializationBenchmark.graphWithImportedFlowBuilder(complexity)

}
