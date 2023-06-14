/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
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
@Threads(8)
@Fork(1)
@State(Scope.Benchmark)
class IdGeneratorBanchmark {
  val threadLocalRandom = IdGenerator.random(ThreadLocalRandom.current())
  val secureRandom = IdGenerator.random(new SecureRandom())

  @Benchmark
  def measureThreadLocalRandom(): Short = threadLocalRandom.nextId()

  @Benchmark
  def measureSecureRandom(): Short = secureRandom.nextId()
}
