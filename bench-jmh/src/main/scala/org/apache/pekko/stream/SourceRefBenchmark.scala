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

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.remote.artery.BenchTestSource
import pekko.stream.scaladsl._

import com.typesafe.config.ConfigFactory

/*
   Just a brief reference run (3.1 GHz Intel Core i7, MacBook Pro late 2017):
   [info] SourceRefBenchmark.source_ref_100k_elements  thrpt   10  724650.336 ± 233643.256  ops/s
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class SourceRefBenchmark {

  val config = ConfigFactory.parseString("""
      pekko {
        log-config-on-start = off
        log-dead-letters-during-shutdown = off
        loglevel = "WARNING"
      }""".stripMargin).withFallback(ConfigFactory.load())

  implicit val system: ActorSystem = ActorSystem("test", config)

  final val successMarker = Success(1)
  final val successFailure = Success(new Exception)

  // safe to be benchmark scoped because the flows we construct in this bench are stateless
  var sourceRef: SourceRef[java.lang.Integer] = _

  //  @Param(Array("16", "32", "128"))
  //  var initialInputBufferSize = 0

  @Setup(Level.Invocation)
  def setup(): Unit = {
    sourceRef = Source.fromGraph(new BenchTestSource(100000)).runWith(StreamRefs.sourceRef())
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  @Benchmark
  @OperationsPerInvocation(100000)
  def source_ref_100k_elements(): Unit = {
    val lock = new Semaphore(1) // todo rethink what is the most lightweight way to await for a streams completion
    lock.acquire()

    sourceRef.source.runWith(Sink.onComplete(_ => lock.release()))

    lock.acquire()
  }

}
