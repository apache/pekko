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

import java.io.{ ByteArrayInputStream, InputStream }
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

/**
 * Compares ByteString.asInputStream and new ByteStreamArray(ByteString.toArray).
 */ 
@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MILLISECONDS)
class ByteString_asInputStream_Benchmark {

  var bs: ByteString = _

  var composed: ByteString = _

  @Param(Array("10", "100", "1000"))
  var kb = 0

  /*
    bench-jmh/jmh:run -f 1 -wi 3 -i 3 .*ByteString_asInputStream_Benchmark.*

    [info] Benchmark                                                             (kb)   Mode  Cnt        Score         Error  Units
    [info] ByteString_asInputStream_Benchmark.composed_bs_as_input_stream          10  thrpt    3    32398.229 ±   26714.266  ops/s
    [info] ByteString_asInputStream_Benchmark.composed_bs_as_input_stream         100  thrpt    3     3642.487 ±     576.459  ops/s
    [info] ByteString_asInputStream_Benchmark.composed_bs_as_input_stream        1000  thrpt    3      285.910 ±      40.463  ops/s
    [info] ByteString_asInputStream_Benchmark.composed_bs_bytes_to_input_stream    10  thrpt    3     6182.509 ±     933.899  ops/s
    [info] ByteString_asInputStream_Benchmark.composed_bs_bytes_to_input_stream   100  thrpt    3      474.634 ±      84.763  ops/s
    [info] ByteString_asInputStream_Benchmark.composed_bs_bytes_to_input_stream  1000  thrpt    3       38.764 ±      49.698  ops/s
    [info] ByteString_asInputStream_Benchmark.single_bs_as_input_stream            10  thrpt    3  2436952.866 ± 1253216.244  ops/s
    [info] ByteString_asInputStream_Benchmark.single_bs_as_input_stream           100  thrpt    3   339116.689 ±  297756.892  ops/s
    [info] ByteString_asInputStream_Benchmark.single_bs_as_input_stream          1000  thrpt    3    32592.451 ±   12465.507  ops/s
    [info] ByteString_asInputStream_Benchmark.single_bs_bytes_to_input_stream      10  thrpt    3   619077.237 ±  200242.708  ops/s
    [info] ByteString_asInputStream_Benchmark.single_bs_bytes_to_input_stream     100  thrpt    3    50481.984 ±   78485.741  ops/s
    [info] ByteString_asInputStream_Benchmark.single_bs_bytes_to_input_stream    1000  thrpt    3     4271.984 ±    1061.978  ops/s
  */

  @Setup
  def setup(): Unit = {
    val bytes = Array.ofDim[Byte](1024 * kb)
    bs = ByteString(bytes)
    composed = ByteString.empty
    for (_ <- 0 to 100) {
      composed = composed ++ bs
    }
  }

  @Benchmark
  def single_bs_bytes_to_input_stream(blackhole: Blackhole): Unit = {
    blackhole.consume(countBytes(new ByteArrayInputStream(bs.toArray)))
  }

  @Benchmark
  def composed_bs_bytes_to_input_stream(blackhole: Blackhole): Unit = {
    blackhole.consume(countBytes(new ByteArrayInputStream(composed.toArray)))
  }

  @Benchmark
  def single_bs_as_input_stream(blackhole: Blackhole): Unit = {
    blackhole.consume(countBytes(bs.asInputStream))
  }

  @Benchmark
  def composed_bs_as_input_stream(blackhole: Blackhole): Unit = {
    blackhole.consume(countBytes(composed.asInputStream))
  }

  private def countBytes(stream: InputStream): Int = {
    val buffer = new Array[Byte](1024)
    var count = 0
    var read = stream.read(buffer)
    while (read != -1) {
      count += read
      read = stream.read(buffer)
    }
    count
  }
}
