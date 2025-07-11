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

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.BenchmarkActors._
import pekko.actor.ForkJoinActorBenchmark.cores

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Threads(1)
@Warmup(iterations = 10, time = 15, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 20, timeUnit = TimeUnit.SECONDS, batchSize = 1)
class AffinityPoolRequestResponseBenchmark {

  @Param(Array("1", "5", "50"))
  var throughPut = 0

  @Param(Array("affinity-dispatcher", "default-fj-dispatcher", "fixed-size-dispatcher"))
  var dispatcher = ""

  @Param(Array("SingleConsumerOnlyUnboundedMailbox")) // "default"
  var mailbox = ""

  final val numThreads, numActors = 8
  final val numQueriesPerActor = 400000
  final val totalNumberOfMessages = numQueriesPerActor * numActors
  final val numUsersInDB = 300000

  implicit var system: ActorSystem = _

  var actors: Vector[(ActorRef, ActorRef)] = null
  var latch: CountDownLatch = null

  @Setup(Level.Trial)
  def setup(): Unit = {

    requireRightNumberOfCores(cores)

    val mailboxConf = mailbox match {
      case "default"                            => ""
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

  @Setup(Level.Invocation)
  def setupActors(): Unit = {
    val (_actors, _latch) =
      RequestResponseActors.startUserQueryActorPairs(numActors, numQueriesPerActor, numUsersInDB, dispatcher)
    actors = _actors
    latch = _latch
  }

  @Benchmark
  @OperationsPerInvocation(totalNumberOfMessages)
  def queryUserServiceActor(): Unit = {
    val startNanoTime = System.nanoTime()
    RequestResponseActors.initiateQuerySimulation(actors, throughPut * 2)
    latch.await(BenchmarkActors.timeout.toSeconds, TimeUnit.SECONDS)
    BenchmarkActors.printProgress(totalNumberOfMessages, numActors, startNanoTime)
  }
}
