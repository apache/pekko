/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery.compress

import java.util.Random

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import org.apache.pekko.util.FastFrequencySketch

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(2)
class CountMinSketchBenchmark {

  @Param(Array("256", "1024", "4096"))
  var capacity: Int = _

  val rand = new Random(20160726)

  val preallocatedIds = Array.ofDim[Int](8192)

  var frequencySketch: FastFrequencySketch[Int] = _

  @Setup
  def init(): Unit = {
    frequencySketch = FastFrequencySketch[Int](capacity)
    (0 to 8191).foreach { index =>
      preallocatedIds(index) = rand.nextInt()
    }
  }

  @Benchmark
  @OperationsPerInvocation(8192)
  def incrementAndFrequency(blackhole: Blackhole): Unit = {
    var i: Int = 0
    while (i < 8192) {
      frequencySketch.increment(preallocatedIds(i))
      blackhole.consume(frequencySketch.frequency(preallocatedIds(i)))
      i += 1
    }
  }

}
