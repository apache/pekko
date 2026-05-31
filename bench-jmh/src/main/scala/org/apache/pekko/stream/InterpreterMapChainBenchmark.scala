/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.stream

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.stream.impl.fusing.GraphInterpreterSpecKit
import pekko.stream.stage._
import InterpreterBenchmark.{ GraphDataSink, GraphDataSource }

/**
 * Companion to [[InterpreterBenchmark]]. That benchmark drives a chain of pure identity stages, so
 * the only work per element is the interpreter's own dispatch — no user code runs. Real pipelines are
 * dominated by `map`/`filter`-style stages whose handlers invoke a user function; in the post-#2986
 * flamegraph that "user code" frame (`push(out, f(grab(in)))`) is ~32% of inclusive samples.
 *
 * This benchmark fills that gap: each stage applies a user function, so the hot path exercises the
 * megamorphic `InHandler.onPush` / `OutHandler.onPull` dispatch plus a real user-code frame. It is the
 * representative surface on which any future interpreter optimisation must be measured — the identity
 * bench alone cannot tell whether a change helps realistic workloads or only the degenerate case.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class InterpreterMapChainBenchmark extends GraphInterpreterSpecKit {
  import InterpreterMapChainBenchmark._

  // manual, and not via @Param, because we want @OperationsPerInvocation on our tests
  final val data100k: Vector[Int] = (1 to 100000).toVector

  @Param(Array("1", "5", "10"))
  var numberOfStages: Int = 0

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

  /**
   * Chain of `map` stages, each applying `_ + 1`. Exercises the chase loop together with virtual
   * handler dispatch and a user-code frame on every element — the realistic counterpart to
   * [[InterpreterBenchmark.graph_interpreter_100k_elements]].
   */
  @Benchmark
  @OperationsPerInvocation(100000)
  def map_chain_100k_elements(): Unit = {
    new TestSetup {
      val maps = Vector.fill(numberOfStages)(new MapStage[Int](_ + 1))
      val source = new GraphDataSource("source", data100k)
      val sink = new GraphDataSink[Int]("sink", data100k.size)

      val b = builder(maps: _*).connect(source, maps.head.in).connect(maps.last.out, sink)

      // FIXME: This should not be here, this is pure setup overhead
      for (i <- 0 until maps.size - 1) {
        b.connect(maps(i).out, maps(i + 1).in)
      }

      b.init()
      sink.requestOne()
      interpreter.execute(Int.MaxValue)
    }
  }
}

object InterpreterMapChainBenchmark {

  /**
   * Per-instance map stage applying a user function. Per-instance (not a shared singleton) for the
   * same reason as [[InterpreterBenchmark.IdentityStage]]: a shared Inlet/Outlet shape would collapse
   * the chain and mis-wire the assembly (`Cannot pull port twice`).
   */
  final class MapStage[T](f: T => T) extends GraphStage[FlowShape[T, T]] {
    val in = Inlet[T]("Map.in")
    val out = Outlet[T]("Map.out")
    override val shape: FlowShape[T, T] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        override def onPush(): Unit = push(out, f(grab(in)))
        override def onPull(): Unit = pull(in)
        setHandler(in, this)
        setHandler(out, this)
      }
  }
}
