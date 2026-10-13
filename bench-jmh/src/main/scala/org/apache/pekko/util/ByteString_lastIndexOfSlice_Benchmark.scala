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

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MILLISECONDS)
class ByteString_lastIndexOfSlice_Benchmark {
  val start = ByteString("abcdefg") ++ ByteString("hijklmno") ++ ByteString("pqrstuv")
  val bss = start ++ start ++ start ++ start ++ start ++ ByteString("xyz")

  val bs = bss.compact // compacted
  val abc = "abc".getBytes(StandardCharsets.UTF_8)
  val hv = "hijklmnopqrstuv".getBytes(StandardCharsets.UTF_8)

  @Benchmark
  def bss_lastIndexOfSlice: Int = bss.lastIndexOfSlice(abc)

  @Benchmark
  def bs_lastIndexOfSlice_abc: Int = bs.lastIndexOfSlice(abc)

  @Benchmark
  def bs_lastIndexOfSlice_hv: Int = bs.lastIndexOfSlice(hv)
}
