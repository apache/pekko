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

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.event._
import pekko.stream.impl.fusing.GraphInterpreterSpecKit
import pekko.stream.impl.fusing.GraphInterpreter.{ DownstreamBoundaryStageLogic, UpstreamBoundaryStageLogic }
import pekko.stream.impl.fusing.GraphStages
import pekko.stream.stage._

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class InterpreterBenchmark {
  import InterpreterBenchmark._

  // manual, and not via @Param, because we want @OperationsPerInvocation on our tests
  final val data100k: Vector[Int] = (1 to 100000).toVector

  @Param(Array("1", "5", "10"))
  var numberOfIds: Int = 0

  @Benchmark
  @OperationsPerInvocation(100000)
  def graph_interpreter_100k_elements(): Unit = {
    new GraphInterpreterSpecKit {
      new TestSetup {
        val identities = Vector.fill(numberOfIds)(GraphStages.identity[Int])
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
}

object InterpreterBenchmark {

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

  val NoopBus = new LoggingBus {
    override def subscribe(subscriber: Subscriber, to: Classifier): Boolean = true
    override def publish(event: Event): Unit = ()
    override def unsubscribe(subscriber: Subscriber, from: Classifier): Boolean = true
    override def unsubscribe(subscriber: Subscriber): Unit = ()
  }
}
