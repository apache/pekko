/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.stream.impl.fusing.GraphInterpreter.{ DownstreamBoundaryStageLogic, UpstreamBoundaryStageLogic }
import pekko.stream.impl.fusing.GraphInterpreterSpecKit
import pekko.stream.stage._

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class InterpreterBenchmark extends GraphInterpreterSpecKit {
  import InterpreterBenchmark._

  // manual, and not via @Param, because we want @OperationsPerInvocation on our tests
  final val data100k: Vector[Int] = (1 to 100000).toVector

  @Param(Array("1", "5", "10"))
  var numberOfIds: Int = 0

  // Earlier this benchmark instantiated `new GraphInterpreterSpecKit` inside @Benchmark, which
  // created (and leaked) a fresh ActorSystem on every invocation and would exhaust native threads
  // on long runs. Extending the SpecKit means JMH's @State(Scope.Benchmark) lifecycle reuses a
  // single ActorSystem across all invocations.

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

  @Benchmark
  @OperationsPerInvocation(100000)
  def graph_interpreter_100k_elements(): Unit = {
    new TestSetup {
      val identities = Vector.fill(numberOfIds)(new IdentityStage[Int])
      val source = new GraphDataSource("source", data100k)
      val sink = new GraphDataSink[Int]("sink", data100k.size)

      val b = builder(identities: _*).connect(source, identities.head.in).connect(identities.last.out, sink)

      // FIXME: This should not be here, this is pure setup overhead
      for (i <- 0 until identities.size - 1) {
        b.connect(identities(i).out, identities(i + 1).in)
      }

      b.init()
      sink.requestOne()
      interpreter.execute(Int.MaxValue)
    }
  }
}

object InterpreterBenchmark {

  /**
   * Per-instance identity stage. Cannot reuse [[GraphStages.identity]] because it is a singleton
   * whose Inlet/Outlet shape is shared across all references — chaining N copies of the singleton
   * collapses to a single shape and mis-wires the assembly (manifests as `Cannot pull port twice`).
   */
  final class IdentityStage[T] extends GraphStage[FlowShape[T, T]] {
    val in = Inlet[T]("Identity.in")
    val out = Outlet[T]("Identity.out")
    override val shape: FlowShape[T, T] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        override def onPush(): Unit = push(out, grab(in))
        override def onPull(): Unit = pull(in)
        setHandler(in, this)
        setHandler(out, this)
      }
  }

  case class GraphDataSource[T](override val toString: String, data: Vector[T]) extends UpstreamBoundaryStageLogic[T] {
    var idx: Int = 0
    override val out: pekko.stream.Outlet[T] = Outlet[T]("out")
    out.id = 0

    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit = {
          if (idx < data.size) {
            push(out, data(idx))
            idx += 1
          } else {
            completeStage()
          }
        }
        override def onDownstreamFinish(cause: Throwable): Unit = cancelStage(cause)
      })
  }

  case class GraphDataSink[T](override val toString: String, var expected: Int)
      extends DownstreamBoundaryStageLogic[T] {
    override val in: pekko.stream.Inlet[T] = Inlet[T]("in")
    in.id = 0

    setHandler(in,
      new InHandler {
        override def onPush(): Unit = {
          expected -= 1
          if (expected > 0) pull(in)
          // Otherwise do nothing, it will exit the interpreter
        }
      })

    def requestOne(): Unit = pull(in)
  }
}
