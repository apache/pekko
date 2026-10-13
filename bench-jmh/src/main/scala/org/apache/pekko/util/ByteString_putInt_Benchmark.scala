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
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(2)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
class ByteString_putInt_Benchmark {

  @Benchmark
  @OperationsPerInvocation(1000)
  def putShortBE(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0
    while (i < 1000) {
      builder.putShort(i)(java.nio.ByteOrder.BIG_ENDIAN)
      i += 1
    }
    bh.consume(builder.result())
  }

  @Benchmark
  @OperationsPerInvocation(1000)
  def putShortLE(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0
    while (i < 1000) {
      builder.putShort(i)(java.nio.ByteOrder.LITTLE_ENDIAN)
      i += 1
    }
    bh.consume(builder.result())
  }

  @Benchmark
  @OperationsPerInvocation(1000)
  def putIntBE(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0
    while (i < 1000) {
      builder.putInt(i)(java.nio.ByteOrder.BIG_ENDIAN)
      i += 1
    }
    bh.consume(builder.result())
  }

  @Benchmark
  @OperationsPerInvocation(1000)
  def putIntLE(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0
    while (i < 1000) {
      builder.putInt(i)(java.nio.ByteOrder.LITTLE_ENDIAN)
      i += 1
    }
    bh.consume(builder.result())
  }

  @Benchmark
  @OperationsPerInvocation(1000)
  def putLongBE(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0L
    while (i < 1000L) {
      builder.putLong(i)(java.nio.ByteOrder.BIG_ENDIAN)
      i += 1L
    }
    bh.consume(builder.result())
  }

  @Benchmark
  @OperationsPerInvocation(1000)
  def putLongLE(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0L
    while (i < 1000L) {
      builder.putLong(i)(java.nio.ByteOrder.LITTLE_ENDIAN)
      i += 1L
    }
    bh.consume(builder.result())
  }

}
