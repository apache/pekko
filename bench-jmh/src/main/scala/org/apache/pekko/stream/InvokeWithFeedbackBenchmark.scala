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
import pekko.actor.ActorSystem
import pekko.stream.scaladsl._

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class InvokeWithFeedbackBenchmark {
  implicit val system: ActorSystem = ActorSystem("InvokeWithFeedbackBenchmark")

  var sourceQueue: SourceQueueWithComplete[Int] = _
  var sinkQueue: SinkQueueWithCancel[Int] = _

  val waitForResult = 100.millis

  @Setup
  def setup(): Unit = {
    // these are currently the only two built in stages using invokeWithFeedback
    val (in, out) =
      Source
        .queue[Int](bufferSize = 1, overflowStrategy = OverflowStrategy.backpressure)
        .toMat(Sink.queue[Int]())(Keep.both)
        .run()

    sourceQueue = in
    sinkQueue = out

  }

  @OperationsPerInvocation(100000)
  @Benchmark
  def pass_through_100k_elements(): Unit = {
    (0 to 100000).foreach { n =>
      val f = sinkQueue.pull()
      Await.result(sourceQueue.offer(n), waitForResult)
      Await.result(f, waitForResult)
    }
  }

  @TearDown
  def tearDown(): Unit = {
    sourceQueue.complete()
    // no way to observe sink completion from the outside
    Await.result(system.terminate(), 5.seconds)
  }

}
