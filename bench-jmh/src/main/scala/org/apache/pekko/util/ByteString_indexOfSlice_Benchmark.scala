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
import java.nio.charset.StandardCharsets

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MILLISECONDS)
class ByteString_indexOfSlice_Benchmark {
  val start = ByteString("abcdefg") ++ ByteString("hijklmno") ++ ByteString("pqrstuv")
  val bss = start ++ start ++ start ++ start ++ start ++ ByteString("xyz")

  val bs = bss.compact // compacted
  val xyz = "xyz".getBytes(StandardCharsets.UTF_8)

  @Benchmark
  def bss_indexOfSlice: Int = bss.indexOfSlice(xyz, 1)

  @Benchmark
  def bs_indexOfSlice: Int = bs.indexOfSlice(xyz, 1)

}
