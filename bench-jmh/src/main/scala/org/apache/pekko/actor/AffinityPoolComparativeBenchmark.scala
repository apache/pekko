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

package org.apache.pekko.actor

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.BenchmarkActors._
import pekko.actor.ForkJoinActorBenchmark.cores

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Threads(1)
@Warmup(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 15, timeUnit = TimeUnit.SECONDS, batchSize = 1)
class AffinityPoolComparativeBenchmark {

  @Param(Array("1"))
  var throughPut = 0

  @Param(Array("affinity-dispatcher", "default-fj-dispatcher", "fixed-size-dispatcher"))
  var dispatcher = ""

  @Param(Array("SingleConsumerOnlyUnboundedMailbox")) // "default"
  var mailbox = ""

  final val numThreads, numActors = 8
  final val numMessagesPerActorPair = 2000000
  final val totalNumberOfMessages = numMessagesPerActorPair * (numActors / 2)

  implicit var system: ActorSystem = _

  @Setup(Level.Trial)
  def setup(): Unit = {

    requireRightNumberOfCores(cores)

    val mailboxConf = mailbox match {
      case "default" => ""
      case "SingleConsumerOnlyUnboundedMailbox" =>
        s"""default-mailbox.mailbox-type = "${classOf[pekko.dispatch.SingleConsumerOnlyUnboundedMailbox].getName}""""
    }

    system = ActorSystem(
      "AffinityPoolComparativeBenchmark",
      ConfigFactory.parseString(s"""| pekko {
          |   log-dead-letters = off
          |   actor {
          |     default-fj-dispatcher {
          |       executor = "fork-join-executor"
          |       fork-join-executor {
          |         parallelism-min = $numThreads
          |         parallelism-factor = 1.0
          |         parallelism-max = $numThreads
          |       }
          |       throughput = $throughPut
          |     }
          |
          |     fixed-size-dispatcher {
          |       executor = "thread-pool-executor"
          |       thread-pool-executor {
          |         fixed-pool-size = $numThreads
          |     }
          |       throughput = $throughPut
          |     }
          |
          |     affinity-dispatcher {
          |       executor = "affinity-pool-executor"
          |       affinity-pool-executor {
          |         parallelism-min = $numThreads
          |         parallelism-factor = 1.0
          |         parallelism-max = $numThreads
          |         task-queue-size = 512
          |         idle-cpu-level = 5
          |         fair-work-distribution.threshold = 2048
          |     }
          |       throughput = $throughPut
          |     }
          |     $mailboxConf
          |   }
          | }
      """.stripMargin))
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = tearDownSystem()

  @Benchmark
  @OperationsPerInvocation(totalNumberOfMessages)
  def pingPong(): Unit = benchmarkPingPongActors(numMessagesPerActorPair, numActors, dispatcher, throughPut, timeout)
}
