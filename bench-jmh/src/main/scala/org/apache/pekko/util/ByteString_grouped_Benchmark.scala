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

package org.apache.pekko.util

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
class ByteString_grouped_Benchmark {

  private val bsLarge = ByteString(Array.ofDim[Byte](1000 * 1000))

  /*
    > bench-jmh/jmh:run -f1 .*ByteString_grouped_Benchmark
    [info] Benchmark                             Mode  Cnt      Score      Error  Units
    [info] ByteString_grouped_Benchmark.grouped  avgt   10  59386.328 ± 1466.045  ns/op
   */

  @Benchmark
  def grouped(bh: Blackhole): Unit =
    bh.consume(bsLarge.grouped(1000).foreach(bh.consume))
}
