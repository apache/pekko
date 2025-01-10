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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.remote.artery.BenchTestSource
import pekko.remote.artery.LatchSink
import pekko.stream.impl.fusing.GraphStages
import pekko.stream.scaladsl._
import pekko.stream.testkit.scaladsl.StreamTestKit

object FlatMapConcatBenchmark {
  final val OperationsPerInvocation = 100000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class FlatMapConcatBenchmark {
  import FlatMapConcatBenchmark._

  private val config = ConfigFactory.parseString("""
    pekko.actor.default-dispatcher {
      executor = "fork-join-executor"
      fork-join-executor {
        parallelism-factor = 1
      }
    }
    """)

  private implicit val system: ActorSystem = ActorSystem("FlatMapConcatBenchmark", config)

  var testSource: Source[java.lang.Integer, NotUsed] = _

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
  def sourceDotSingle(): Unit = {
    val latch = new CountDownLatch(1)

    testSource.flatMapConcat(Source.single).runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def sourceDotSingleP1(): Unit = {
    val latch = new CountDownLatch(1)

    testSource.flatMapConcat(1, Source.single).runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def internalSingleSource(): Unit = {
    val latch = new CountDownLatch(1)

    testSource
      .flatMapConcat(elem => new GraphStages.SingleSource(elem))
      .runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def internalSingleSourceP1(): Unit = {
    val latch = new CountDownLatch(1)

    testSource
      .flatMapConcat(1, elem => new GraphStages.SingleSource(elem))
      .runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def oneElementList(): Unit = {
    val latch = new CountDownLatch(1)

    testSource.flatMapConcat(n => Source(n :: Nil)).runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def oneElementListP1(): Unit = {
    val latch = new CountDownLatch(1)

    testSource.flatMapConcat(1, n => Source(n :: Nil)).runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def completedFuture(): Unit = {
    val latch = new CountDownLatch(1)

    testSource
      .flatMapConcat(n => Source.future(Future.successful(n)))
      .runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def completedFutureP1(): Unit = {
    val latch = new CountDownLatch(1)

    testSource
      .flatMapConcat(1, n => Source.future(Future.successful(n)))
      .runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def normalFuture(): Unit = {
    val latch = new CountDownLatch(1)

    testSource
      .flatMapConcat(n => Source.future(Future(n)(system.dispatcher)))
      .runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def normalFutureP1(): Unit = {
    val latch = new CountDownLatch(1)

    testSource
      .flatMapConcat(1, n => Source.future(Future(n)(system.dispatcher)))
      .runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def mapBaseline(): Unit = {
    val latch = new CountDownLatch(1)

    testSource.map(elem => elem).runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  private def awaitLatch(latch: CountDownLatch): Unit = {
    if (!latch.await(30, TimeUnit.SECONDS)) {
      implicit val ec = system.dispatcher
      StreamTestKit.printDebugDump(SystemMaterializer(system).materializer.supervisor)
      throw new RuntimeException("Latch didn't complete in time")
    }
  }

}
