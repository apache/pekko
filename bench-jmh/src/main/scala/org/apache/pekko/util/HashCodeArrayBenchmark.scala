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
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class HashCodeArrayBenchmark {

  @Param(Array("16", "256"))
  var size: Int = _

  private var ints: Array[Int] = _
  private var refs: Array[AnyRef] = _

  @Setup
  def setup(): Unit = {
    ints = Array.tabulate(size)(identity)
    refs = Array.tabulate[AnyRef](size)(index => Integer.valueOf(index))
  }

  @Benchmark
  def hashIntArray(): Int = HashCode.hash(HashCode.SEED, ints)

  @Benchmark
  def hashReferenceArray(): Int = HashCode.hash(HashCode.SEED, refs)
}
