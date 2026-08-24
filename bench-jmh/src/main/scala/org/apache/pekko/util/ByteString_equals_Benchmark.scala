/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.util

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MILLISECONDS)
class ByteString_equals_Benchmark {

  private def bytes(n: Int): Array[Byte] = Array.tabulate[Byte](n)(i => (i % 251).toByte)

  private def fragmented(fragments: Int, fragmentSize: Int): ByteString =
    (0 until fragments).foldLeft(ByteString.empty) { (acc, i) =>
      acc ++ ByteString(Array.tabulate[Byte](fragmentSize)(j => ((i * fragmentSize + j) % 251).toByte))
    }

  // compacted, single backing array
  val flatA: ByteString = ByteString(bytes(64 * 1024)).compact
  val flatB: ByteString = ByteString(bytes(64 * 1024)).compact

  // same content, but spread over 1024 fragments
  val ropeA: ByteString = fragmented(1024, 64)
  val ropeB: ByteString = fragmented(1024, 64)

  // differs in the very first byte: measures the early-exit path
  val flatDiffersFirst: ByteString = {
    val b = bytes(64 * 1024)
    b(0) = (b(0) ^ 0xFF).toByte
    ByteString(b).compact
  }

  // differs only in the last byte: forces a full scan before returning false
  val flatDiffersLast: ByteString = {
    val b = bytes(64 * 1024)
    b(b.length - 1) = (b(b.length - 1) ^ 0xFF).toByte
    ByteString(b).compact
  }

  val smallA: ByteString = ByteString(bytes(8))
  val smallB: ByteString = ByteString(bytes(8))

  /*
  Measured with: bench-jmh/jmh:run -f1 -wi 3 -i 3 -w 1s -r 1s .*ByteString_equals.*
  (short run; the wide error bars reflect the low iteration count, the differences are far larger)

  Before -- equality inherited from Seq, comparing element by element through iterator and
  boxing every Byte:

  ByteString_equals_Benchmark.flat_differs_first  thrpt    3  181247559.958 ± 61693364.344  ops/s
  ByteString_equals_Benchmark.flat_differs_last   thrpt    3       6427.248 ±     4360.116  ops/s
  ByteString_equals_Benchmark.flat_equal_flat     thrpt    3      17772.167 ±     6425.067  ops/s
  ByteString_equals_Benchmark.flat_equal_rope     thrpt    3       1938.294 ±     1108.938  ops/s
  ByteString_equals_Benchmark.rope_equal_rope     thrpt    3        959.603 ±     2045.986  ops/s
  ByteString_equals_Benchmark.small_equal         thrpt    3   53499809.406 ± 21113673.795  ops/s

  After -- array comparison reusing the SWAR-based matchesAt, plus the memoised fragment
  lookup in ByteStrings.byteAtUnchecked:

  ByteString_equals_Benchmark.flat_differs_first  thrpt    3  289835597.355 ± 76041571.839  ops/s
  ByteString_equals_Benchmark.flat_differs_last   thrpt    3      92604.106 ±    50614.043  ops/s
  ByteString_equals_Benchmark.flat_equal_flat     thrpt    3      70767.215 ±    47918.719  ops/s
  ByteString_equals_Benchmark.flat_equal_rope     thrpt    3      32567.330 ±    16392.230  ops/s
  ByteString_equals_Benchmark.rope_equal_rope     thrpt    3      25819.355 ±    15453.821  ops/s
  ByteString_equals_Benchmark.small_equal         thrpt    3  106841120.007 ± 11546486.517  ops/s

  hashCode is unchanged by this work and is included only to show it stays flat.
   */

  @Benchmark
  def flat_equal_flat: Boolean = flatA == flatB

  @Benchmark
  def rope_equal_rope: Boolean = ropeA == ropeB

  @Benchmark
  def flat_equal_rope: Boolean = flatA == ropeB

  @Benchmark
  def flat_differs_first: Boolean = flatA == flatDiffersFirst

  @Benchmark
  def flat_differs_last: Boolean = flatA == flatDiffersLast

  @Benchmark
  def small_equal: Boolean = smallA == smallB

  @Benchmark
  def flat_hashCode: Int = flatA.hashCode

  @Benchmark
  def rope_hashCode: Int = ropeA.hashCode
}
