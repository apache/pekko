/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
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
