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
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{ Await, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko.util.Timeout

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(2)
@Warmup(iterations = 10, time = 1700, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 1700, timeUnit = TimeUnit.MILLISECONDS)
class ScheduleBenchmark {
  implicit val system: ActorSystem = ActorSystem()
  val scheduler: Scheduler = system.scheduler
  val interval: FiniteDuration = 25.millis
  val within: FiniteDuration = 2.seconds
  implicit val timeout: Timeout = Timeout(within)

  @Param(Array("4", "16", "64"))
  var to = 0

  @Param(Array("0.1", "0.35", "0.9"))
  var ratio = 0d

  var winner: Int = _
  var promise: Promise[Any] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    winner = (to * ratio + 1).toInt
    promise = Promise[Any]()
  }

  @TearDown
  def shutdown(): Unit = {
    system.terminate()
    system.terminateAndAwait(15.seconds)
  }

  def op(idx: Int) = if (idx == winner) promise.trySuccess(idx) else idx

  @Benchmark
  def scheduleWithFixedDelay(): Unit = {
    val aIdx = new AtomicInteger(1)
    val tryWithNext = scheduler.scheduleWithFixedDelay(0.millis, interval) { () =>
      val idx = aIdx.getAndIncrement
      if (idx <= to) op(idx)
    }
    promise.future.onComplete {
      case _ =>
        tryWithNext.cancel()
    }
    Await.result(promise.future, within)
  }

  @Benchmark
  def scheduleAtFixedRate(): Unit = {
    val aIdx = new AtomicInteger(1)
    val tryWithNext = scheduler.scheduleAtFixedRate(0.millis, interval) { () =>
      val idx = aIdx.getAndIncrement
      if (idx <= to) op(idx)
    }
    promise.future.onComplete {
      case _ =>
        tryWithNext.cancel()
    }
    Await.result(promise.future, within)
  }

  @Benchmark
  def multipleScheduleOnce(): Unit = {
    val tryWithNext = (1 to to)
      .foldLeft(0.millis -> List[Cancellable]()) {
        case ((interv, c), idx) =>
          (interv + interval,
            scheduler.scheduleOnce(interv) {
              op(idx)
            } :: c)
      }
      ._2
    promise.future.onComplete {
      case _ =>
        tryWithNext.foreach(_.cancel())
    }
    Await.result(promise.future, within)
  }
}
