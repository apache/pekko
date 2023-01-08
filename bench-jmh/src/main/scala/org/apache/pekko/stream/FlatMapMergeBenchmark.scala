/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import java.util.concurrent.TimeUnit

import scala.concurrent._
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.{ Done, NotUsed }
import pekko.actor.ActorSystem
import pekko.remote.artery.BenchTestSourceSameElement
import pekko.stream.scaladsl._

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class FlatMapMergeBenchmark {
  implicit val system: ActorSystem = ActorSystem("FlatMapMergeBenchmark")

  val NumberOfElements = 100000

  @Param(Array("0", "1", "10"))
  var NumberOfStreams = 0

  var graph: RunnableGraph[Future[Done]] = _

  def createSource(count: Int): Graph[SourceShape[java.lang.Integer], NotUsed] =
    new BenchTestSourceSameElement(count, 1)

  @Setup
  def setup(): Unit = {
    val source = NumberOfStreams match {
      // Base line: process NumberOfElements-many elements from a single source without using flatMapMerge
      case 0 => createSource(NumberOfElements)
      // Stream merging: process NumberOfElements-many elements from n sources, each producing (NumberOfElements/n)-many elements
      case n =>
        val subSource = createSource(NumberOfElements / n)
        Source.repeat(()).take(n).flatMapMerge(n, _ => subSource)
    }
    graph = Source.fromGraph(source).toMat(Sink.ignore)(Keep.right)
    // eager init of materializer
    SystemMaterializer(system).materializer
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  @Benchmark
  @OperationsPerInvocation(100000) // Note: needs to match NumberOfElements.
  def flat_map_merge_100k_elements(): Unit = {
    Await.result(graph.run(), Duration.Inf)
  }
}
