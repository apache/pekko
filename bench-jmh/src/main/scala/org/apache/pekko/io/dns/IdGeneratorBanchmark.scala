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

package org.apache.pekko.io.dns

import org.openjdk.jmh.annotations.{
  Benchmark,
  BenchmarkMode,
  Fork,
  Measurement,
  Mode,
  OutputTimeUnit,
  Scope,
  State,
  Threads,
  Warmup
}

import java.util.concurrent.{ ThreadLocalRandom, TimeUnit }
import java.security.SecureRandom

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5)
@Fork(1)
@State(Scope.Benchmark)
class IdGeneratorBanchmark {
  val threadLocalRandom = IdGenerator.random(ThreadLocalRandom.current())
  val secureRandom = IdGenerator.random(new SecureRandom())
  val enhancedDoubleHash = new IdGenerator.EnhancedDoubleHashGenerator(new SecureRandom())

  @Threads(1)
  @Benchmark
  def measureThreadLocalRandom(): Short = threadLocalRandom.nextId()

  @Threads(1)
  @Benchmark
  def measureSecureRandom(): Short = secureRandom.nextId()

  @Threads(1)
  @Benchmark
  def measureEnhancedDoubleHash(): Short = enhancedDoubleHash.nextId()

  @Threads(2)
  @Benchmark
  def multipleThreadsMeasureThreadLocalRandom(): Short = threadLocalRandom.nextId()

  @Threads(2)
  @Benchmark
  def multipleThreadsMeasureSecureRandom(): Short = secureRandom.nextId()

  @Threads(2)
  @Benchmark
  def multipleThreadsMeasureEnhancedDoubleHash(): Short = enhancedDoubleHash.nextId()
}
