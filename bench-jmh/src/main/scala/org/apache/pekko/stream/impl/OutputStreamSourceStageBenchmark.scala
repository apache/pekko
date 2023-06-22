/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.annotations.TearDown

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.StreamConverters

object OutputStreamSourceStageBenchmark {
  final val WritesPerBench = 10000
}
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class OutputStreamSourceStageBenchmark {
  import OutputStreamSourceStageBenchmark.WritesPerBench
  implicit val system: ActorSystem = ActorSystem("OutputStreamSourceStageBenchmark")

  private val bytes: Array[Byte] = Array.emptyByteArray

  @Benchmark
  @OperationsPerInvocation(WritesPerBench)
  def consumeWrites(): Unit = {
    val (os, done) = StreamConverters.asOutputStream().toMat(Sink.ignore)(Keep.both).run()
    new Thread(new Runnable {
      def run(): Unit = {
        var counter = 0
        while (counter > WritesPerBench) {
          os.write(bytes)
          counter += 1
        }
        os.close()
      }
    }).start()
    Await.result(done, 30.seconds)
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

}
