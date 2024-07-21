/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.remote.artery.BenchTestSource
import pekko.remote.artery.LatchSink
import pekko.stream.scaladsl._
import pekko.stream.testkit.scaladsl.StreamTestKit

object MapAsyncBenchmark {
  final val OperationsPerInvocation = 100000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class MapAsyncBenchmark {
  import MapAsyncBenchmark._

  val config = ConfigFactory.parseString("""
    pekko.actor.default-dispatcher {
      executor = "fork-join-executor"
      fork-join-executor {
        parallelism-factor = 1
      }
    }
    """)

  implicit val system: ActorSystem = ActorSystem("MapAsyncBenchmark", config)
  import system.dispatcher

  var testSource: Source[java.lang.Integer, NotUsed] = _

  @Param(Array("1", "4"))
  var parallelism = 0

  @Param(Array("false", "true"))
  var spawn = false

  @Setup
  def setup(): Unit = {
    // eager init of materializer
    SystemMaterializer(system).materializer
    testSource = Source.fromGraph(new BenchTestSource(OperationsPerInvocation))
  }

  @TearDown
  def shutdown(): Unit =
    Await.result(system.terminate(), 5.seconds)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def mapAsync(): Unit = {
    val latch = new CountDownLatch(1)

    testSource
      .mapAsync(parallelism)(elem => if (spawn) Future(elem) else Future.successful(elem))
      .runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def mapAsyncUnordered(): Unit = {
    val latch = new CountDownLatch(1)

    testSource
      .mapAsyncUnordered(parallelism)(elem => if (spawn) Future(elem) else Future.successful(elem))
      .runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  private def awaitLatch(latch: CountDownLatch): Unit =
    if (!latch.await(30, TimeUnit.SECONDS)) {
      StreamTestKit.printDebugDump(SystemMaterializer(system).materializer.supervisor)
      throw new RuntimeException("Latch didn't complete in time")
    }

}
