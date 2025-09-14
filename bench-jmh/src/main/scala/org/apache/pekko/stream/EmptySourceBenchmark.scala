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

import java.util.concurrent.TimeUnit

import scala.concurrent._
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl._

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class EmptySourceBenchmark {
  implicit val system: ActorSystem = ActorSystem("EmptySourceBenchmark")

  @TearDown
  def shutdown(): Unit = {
    system.terminateAndAwait(5.seconds)
  }

  val setup = Source.empty[String].toMat(Sink.ignore)(Keep.right)

  @Benchmark def empty(): Unit =
    Await.result(setup.run(), Duration.Inf)

  /*
    (not serious benchmark, just ballpark check: run on macbook 15, late 2013)

    While it was a PublisherSource:
     [info] EmptySourceBenchmark.empty  thrpt   10  11.219 ± 6.498  ops/ms

    Rewrite to GraphStage:
     [info] EmptySourceBenchmark.empty  thrpt   10  17.556 ± 2.865  ops/ms

   */
}
