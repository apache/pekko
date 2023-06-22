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
class ByteString_indexOf_Benchmark {
  val start = ByteString("abcdefg") ++ ByteString("hijklmno") ++ ByteString("pqrstuv")
  val bss = start ++ start ++ start ++ start ++ start ++ ByteString("xyz")

  val bs = bss.compact // compacted

  /*
  original
  ByteString_indexOf_Benchmark.bs1_indexOf_from                 thrpt   20     999335.124 ±  234047.176  ops/s
  ByteString_indexOf_Benchmark.bss_indexOf_from_best_case       thrpt   20   42735542.833 ± 1082874.815  ops/s
  ByteString_indexOf_Benchmark.bss_indexOf_from_far_index_case  thrpt   20    4941422.104 ±  109132.224  ops/s
  ByteString_indexOf_Benchmark.bss_indexOf_from_worst_case      thrpt   20     328123.207 ±   16550.271  ops/s

  optimized
  ByteString_indexOf_Benchmark.bs1_indexOf_from                 thrpt   20  339488707.553 ± 9680274.621  ops/s
  ByteString_indexOf_Benchmark.bss_indexOf_from_best_case       thrpt   20  126385479.889 ± 3644024.423  ops/s
  ByteString_indexOf_Benchmark.bss_indexOf_from_far_index_case  thrpt   20   14282036.963 ±  529652.214  ops/s
  ByteString_indexOf_Benchmark.bss_indexOf_from_worst_case      thrpt   20    7815676.051 ±  323031.073  ops/s

   */

  @Benchmark
  def bss_indexOf_from_worst_case: Int = bss.indexOf('z', 1)

  @Benchmark
  def bss_indexOf_from_far_index_case: Int = bss.indexOf('z', 109)

  @Benchmark
  def bss_indexOf_from_best_case: Int = bss.indexOf('a', 0)

  @Benchmark
  def bs1_indexOf_from: Int = bs.indexOf('ö', 5)

}
