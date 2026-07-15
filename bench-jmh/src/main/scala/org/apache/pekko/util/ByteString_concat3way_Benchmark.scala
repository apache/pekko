/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.util

import java.nio.{ ByteBuffer, ByteOrder }
import java.util.concurrent.TimeUnit

import org.apache.pekko.util.ByteString.{ ByteString1C, ByteString2 }

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

/**
 * Directional benchmark for PR #2924, comparing the two candidate strategies for
 * the gRPC-shaped "5-byte header ++ payload" hot path:
 *
 *   - copy: caller assembles the result into a fresh array and wraps with
 *           `ByteString.fromArrayUnsafe` — yields a contiguous `ByteString1C`.
 *   - bs2:  `ByteString2.apply(header, payload)` — the new zero-copy 2-fragment impl.
 *
 * For each strategy we time the construction plus every realistic downstream op
 * (parser reads, writer ops, slicing). Sizes cover unary RPC headers (5+64B),
 * typical RPC bodies (5+1KB), medium streaming chunks (5+16KB) and large streaming
 * chunks (5+64KB, 5+256KB).
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
class ByteString_concat3way_Benchmark {

  @Param(Array("64", "1024", "16384", "65536", "262144"))
  var payloadSize: Int = _

  private implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

  // 5-byte gRPC frame header: 1 compressed flag + 4 length BE
  private val headerArr: Array[Byte] = Array[Byte](0, 0, 0, 0, 0)
  private var payloadArr: Array[Byte] = _
  private var header1C: ByteString1C = _
  private var payload1C: ByteString1C = _

  private var preCopy: ByteString = _
  private var preBs2: ByteString = _

  private var outBuf: ByteBuffer = _

  @Setup
  def setup(): Unit = {
    payloadArr = Array.fill[Byte](payloadSize)(42)
    header1C = ByteString.fromArrayUnsafe(headerArr).asInstanceOf[ByteString1C]
    payload1C = ByteString.fromArrayUnsafe(payloadArr).asInstanceOf[ByteString1C]
    preCopy = doCopy()
    preBs2 = doBs2()
    outBuf = ByteBuffer.allocate(headerArr.length + payloadSize)
  }

  // === concat-only (allocation cost) ===

  @Benchmark
  def concat_copy(): ByteString = doCopy()

  @Benchmark
  def concat_bs2(): ByteString = doBs2()

  // === concat + parser reads via iterator (naive parser path) ===

  @Benchmark
  def concatIter_copy(): Int = {
    val r = doCopy()
    r.iterator.getInt + r(4).toInt
  }

  @Benchmark
  def concatIter_bs2(): Int = {
    val r = doBs2()
    r.iterator.getInt + r(4).toInt
  }

  // === concat + parser reads via readIntBE (efficient parser path) ===

  @Benchmark
  def concatRead_copy(): Int = {
    val r = doCopy()
    r.readIntBE(0) + r(4).toInt
  }

  @Benchmark
  def concatRead_bs2(): Int = {
    val r = doBs2()
    r.readIntBE(0) + r(4).toInt
  }

  // === concat + copyToBuffer (Netty/NIO write path) ===

  @Benchmark
  def concatCopyToBuf_copy(): Int = {
    outBuf.clear()
    doCopy().copyToBuffer(outBuf)
  }

  @Benchmark
  def concatCopyToBuf_bs2(): Int = {
    outBuf.clear()
    doBs2().copyToBuffer(outBuf)
  }

  // === concat + asByteBuffers (gather I/O write path) ===

  @Benchmark
  def concatAsByteBuffers_copy(bh: Blackhole): Unit =
    bh.consume(doCopy().asByteBuffers)

  @Benchmark
  def concatAsByteBuffers_bs2(bh: Blackhole): Unit =
    bh.consume(doBs2().asByteBuffers)

  // === concat + drop(5) (strip header) ===

  @Benchmark
  def concatDrop5_copy(): ByteString = doCopy().drop(5)

  @Benchmark
  def concatDrop5_bs2(): ByteString = doBs2().drop(5)

  // === concat + take(5) (extract header) ===

  @Benchmark
  def concatTake5_copy(): ByteString = doCopy().take(5)

  @Benchmark
  def concatTake5_bs2(): ByteString = doBs2().take(5)

  // === downstream-only on pre-built results ===

  @Benchmark
  def downIter_copy(): Int = preCopy.iterator.getInt + preCopy(4).toInt
  @Benchmark
  def downIter_bs2(): Int = preBs2.iterator.getInt + preBs2(4).toInt

  @Benchmark
  def downRead_copy(): Int = preCopy.readIntBE(0) + preCopy(4).toInt
  @Benchmark
  def downRead_bs2(): Int = preBs2.readIntBE(0) + preBs2(4).toInt

  @Benchmark
  def downCopyToBuf_copy(bh: Blackhole): Unit = {
    outBuf.clear(); bh.consume(preCopy.copyToBuffer(outBuf))
  }
  @Benchmark
  def downCopyToBuf_bs2(bh: Blackhole): Unit = {
    outBuf.clear(); bh.consume(preBs2.copyToBuffer(outBuf))
  }

  @Benchmark
  def downAsByteBuffers_copy(bh: Blackhole): Unit = bh.consume(preCopy.asByteBuffers)
  @Benchmark
  def downAsByteBuffers_bs2(bh: Blackhole): Unit = bh.consume(preBs2.asByteBuffers)

  // ---- helpers ----

  private def doCopy(): ByteString = {
    val merged = new Array[Byte](headerArr.length + payloadSize)
    System.arraycopy(headerArr, 0, merged, 0, headerArr.length)
    System.arraycopy(payloadArr, 0, merged, headerArr.length, payloadSize)
    ByteString.fromArrayUnsafe(merged)
  }

  private def doBs2(): ByteString =
    ByteString2.apply(header1C, payload1C)
}
