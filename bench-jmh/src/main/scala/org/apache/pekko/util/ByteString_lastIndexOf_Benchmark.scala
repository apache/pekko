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

@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MILLISECONDS)
class ByteString_lastIndexOf_Benchmark {
  val start = ByteString("abcdefg") ++ ByteString("hijklmno") ++ ByteString("pqrstuv")
  val bss = start ++ start ++ start ++ start ++ start ++ ByteString("xyz")

  val bs = bss.compact // compacted
  val bsLen = bs.length
  val aByte = 'a'.toByte
  val oByte = 'o'.toByte

  @Benchmark
  def bss_lastIndexOf_worst_case: Int = bss.lastIndexOf('a')

  @Benchmark
  def bss_lastIndexOf_far_index_case: Int = bss.lastIndexOf('a', 10)

  @Benchmark
  def bss_lastIndexOf_far_index_case_byte: Int = bss.lastIndexOf(aByte, 10)

  @Benchmark
  def bss_lastIndexOf_best_case: Int = bss.lastIndexOf('z')

  @Benchmark
  def bs1_lastIndexOf: Int = bs.lastIndexOf('o', bsLen - 5)

  @Benchmark
  def bs1_lastIndexOf_byte: Int = bs.lastIndexOf(oByte, bsLen - 5)

}
