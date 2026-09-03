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

import org.apache.pekko
import pekko.util.ByteString.{ ByteString1, ByteStrings }

/**
 * Exercises `ByteStrings.byteAtUnchecked`, which resolves an absolute offset to a fragment.
 *
 * `ByteStrings.apply` delegates to `byteAtUnchecked` after a bounds check, so indexed access,
 * sequential traversal and `map` all share the memoised fragment lookup.
 */
@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MILLISECONDS)
class ByteString_byteAtUnchecked_Benchmark {

  // 1024 single-byte fragments: the worst case for locating a fragment by offset
  val manyFragments: ByteString = ByteStrings(Vector.tabulate(1024)(i => ByteString1(Array((i % 251).toByte))))

  // same content, 64 fragments of 16 bytes
  val fewFragments: ByteString =
    ByteStrings(Vector.tabulate(64)(i => ByteString1(Array.tabulate[Byte](16)(j => ((i * 16 + j) % 251).toByte))))

  val identity: Byte => Byte = b => b

  /*
  Measured with: bench-jmh/jmh:run -f1 -wi 3 -i 3 -w 1s -r 1s .*ByteString_byteAtUnchecked.*
  (short run; the error bars are wide, but the sequential difference is far larger than the noise)

  Before -- every offset was resolved by scanning the fragment vector from the start:

  fewFragments_map          thrpt    3  392361.866 ± 1027423.684  ops/s
  manyFragments_map         thrpt    3   91349.440 ±  123062.017  ops/s
  manyFragments_random      thrpt    3     508.262 ±     291.734  ops/s
  manyFragments_reverse     thrpt    3     638.345 ±     498.546  ops/s
  manyFragments_sequential  thrpt    3     650.823 ±     584.320  ops/s

  After -- byteAtUnchecked remembers the fragment resolved by the previous call:

  fewFragments_map          thrpt    3  369418.384 ±  890396.160  ops/s
  manyFragments_map         thrpt    3  104726.270 ±  117780.717  ops/s
  manyFragments_random      thrpt    3     596.351 ±     783.657  ops/s
  manyFragments_reverse     thrpt    3     401.330 ±      52.761  ops/s
  manyFragments_sequential  thrpt    3   54838.759 ±   29275.476  ops/s

  Sequential access is roughly 84x faster. Random access is unchanged (the hint never hits) and
  reverse access did not benefit at that point, since each step landed before the remembered
  fragment and fell back to a scan from the start -- both stayed within the noise of the
  previous numbers.

  After resolveFragment also resumes backward from the remembered fragment (same short run,
  same wide error bars):

  manyFragments_reverse     thrpt    3    386.052 ±  550.638  ops/s   (before, on this machine)
  manyFragments_reverse     thrpt    3  39969.362 ± 49806.718 ops/s   (after)
  manyFragments_sequential  thrpt    3  43415.035 ± 45223.244 ops/s   (before, on this machine)
  manyFragments_sequential  thrpt    3  44624.286 ± 42770.609 ops/s   (after)

  Reverse access is roughly 100x faster and on par with sequential; sequential is unchanged
  within the noise.
   */

  private val randomIndices: Array[Int] = {
    val random = new scala.util.Random(0)
    Array.fill(1024)(random.nextInt(1024))
  }

  @Benchmark
  def manyFragments_sequential: Int = {
    var sum = 0
    var i = 0
    while (i < manyFragments.length) {
      sum += manyFragments(i)
      i += 1
    }
    sum
  }

  @Benchmark
  def manyFragments_reverse: Int = {
    var sum = 0
    var i = manyFragments.length - 1
    while (i >= 0) {
      sum += manyFragments(i)
      i -= 1
    }
    sum
  }

  @Benchmark
  def manyFragments_random: Int = {
    var sum = 0
    var i = 0
    while (i < randomIndices.length) {
      sum += manyFragments(randomIndices(i))
      i += 1
    }
    sum
  }

  @Benchmark
  def manyFragments_map: ByteString = manyFragments.map(identity)

  @Benchmark
  def fewFragments_map: ByteString = fewFragments.map(identity)
}
