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

package org.apache.pekko.actor

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko.actor.BenchmarkActors._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Threads(1)
@Warmup(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 15, timeUnit = TimeUnit.SECONDS, batchSize = 1)
class AffinityPoolIdleCPULevelBenchmark {

  final val numThreads, numActors = 8
  final val numMessagesPerActorPair = 2000000
  final val totalNumberOfMessages = numMessagesPerActorPair * (numActors / 2)

  implicit var system: ActorSystem = _

  @Param(Array("1", "3", "5", "7", "10"))
  var idleCPULevel = ""

  @Param(Array("25"))
  var throughPut = 0

  @Setup(Level.Trial)
  def setup(): Unit = {

    requireRightNumberOfCores(numThreads)

    system = ActorSystem(
      "AffinityPoolWaitingStrategyBenchmark",
      ConfigFactory.parseString(s""" | pekko {
         |   log-dead-letters = off
         |   actor {
         |     affinity-dispatcher {
         |       executor = "affinity-pool-executor"
         |       affinity-pool-executor {
         |         parallelism-min = $numThreads
         |         parallelism-factor = 1.0
         |         parallelism-max = $numThreads
         |         task-queue-size = 512
         |         idle-cpu-level = $idleCPULevel
         |         fair-work-distribution.threshold = 2048
         |     }
         |     throughput = $throughPut
         |    }
         |
         |   }
         | }
      """.stripMargin))
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = tearDownSystem()

  @Benchmark
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @OperationsPerInvocation(8000000)
  def pingPong(): Unit =
    benchmarkPingPongActors(numMessagesPerActorPair, numActors, "affinity-dispatcher", throughPut, timeout)

}
