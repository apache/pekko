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

import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

/*
regex checking:
[info] a.a.ActorCreationBenchmark.synchronousStarting       ss    120000       28.285        0.481       us

hand checking:
[info] a.a.ActorCreationBenchmark.synchronousStarting       ss    120000       21.496        0.502       us


 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@Fork(5)
@Warmup(iterations = 1000)
@Measurement(iterations = 4000)
class ActorCreationBenchmark {
  implicit val system: ActorSystem = ActorSystem()

  final val props = Props[MyActor]()

  var i = 1
  def name = {
    i += 1
    "some-rather-long-actor-name-actor-" + i
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    system.terminate()
    system.terminateAndAwait(15.seconds)
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def synchronousStarting =
    system.actorOf(props, name)
}

class MyActor extends Actor {
  override def receive: Receive = {
    case _ =>
  }
}
