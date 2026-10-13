/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(2)
@Warmup(iterations = 4)
@Measurement(iterations = 10)
class ByteString_append_Benchmark {

  private val bs = ByteString(Array.ofDim[Byte](10))
  private val header = ByteString.fromArrayUnsafe(Array.tabulate[Byte](9)(i => (i + 1).toByte))
  private val payload = ByteString.fromArrayUnsafe(Array.fill[Byte](4096)(42))
  private val frame = header ++ payload
  private val shortHeaderPrefix = ByteString.fromArrayUnsafe(Array[Byte](1, 2))
  private val headerTailAndPayload = ByteString.fromArrayUnsafe(Array.tabulate[Byte](4096 + 7)(i => (i + 3).toByte))
  private val crossBoundaryFrame = shortHeaderPrefix ++ headerTailAndPayload
  private val compactFrame = frame.compact
  private val outputBuffer = ByteBuffer.allocateDirect(9 + 4096)
  private val outputArray = new Array[Byte](9 + 4096)

  @Benchmark
  @OperationsPerInvocation(10000)
  def appendThree(bh: Blackhole): Unit = {
    var result = ByteString.empty
    var i = 0
    while (i < 10000) {
      result = result ++ bs
      if (i % 3 == 0) {
        bh.consume(result)
        result = ByteString.empty
      }
      i += 1
    }
    bh.consume(result)
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def appendMany(bh: Blackhole): Unit = {
    var result = ByteString.empty
    var i = 0
    while (i < 10000) {
      result = result ++ bs
      i += 1
    }
    bh.consume(result)
  }

  @Benchmark
  def appendTwo(): ByteString =
    header ++ payload

  @Benchmark
  def readTwoPartHeader(): Int =
    frame.readIntBE(0) + frame.readIntBE(4) + frame(8)

  @Benchmark
  def readTwoPartCrossBoundaryHeader(): Int =
    crossBoundaryFrame.readIntBE(0) + crossBoundaryFrame.readIntBE(2) + crossBoundaryFrame(8)

  @Benchmark
  def readCompactHeader(): Int =
    compactFrame.readIntBE(0) + compactFrame.readIntBE(4) + compactFrame(8)

  @Benchmark
  def appendTwoAndReadHeader(): Int = {
    val frame = header ++ payload
    frame.readIntBE(0) + frame.readIntBE(4) + frame(8)
  }

  @Benchmark
  def appendTwoAndReadCrossBoundaryHeader(): Int = {
    val frame = shortHeaderPrefix ++ headerTailAndPayload
    frame.readIntBE(0) + frame.readIntBE(2) + frame(8)
  }

  @Benchmark
  def appendTwoAndCopyToBuffer(): Int = {
    outputBuffer.clear()
    (header ++ payload).copyToBuffer(outputBuffer)
  }

  @Benchmark
  def appendTwoAndCopyToArray(): Int = (header ++ payload).copyToArray(outputArray, 0, outputArray.length)

  @Benchmark
  @OperationsPerInvocation(10000)
  def builderOne(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0
    while (i < 10000) {
      builder ++= bs
      bh.consume(builder.result())
      builder.clear()
      i += 1
    }
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def builderThree(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0
    while (i < 10000) {
      builder ++= bs
      if (i % 3 == 0) {
        bh.consume(builder.result())
        builder.clear()
      }
      i += 1
    }
    bh.consume(builder.result())
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def builderFive(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0
    while (i < 10000) {
      builder ++= bs
      if (i % 5 == 0) {
        bh.consume(builder.result())
        builder.clear()
      }
      i += 1
    }
    bh.consume(builder.result())
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def builderMany(bh: Blackhole): Unit = {
    val builder = ByteString.newBuilder
    var i = 0
    while (i < 10000) {
      builder ++= bs
      i += 1
    }
    bh.consume(builder.result())
  }
}
