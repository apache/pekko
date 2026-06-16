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

import org.apache.pekko
import pekko.stream.impl.io.ByteStringParser

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MILLISECONDS)
class ByteStringParser_readNum_Benchmark {
  val start = ByteString("abcdefg") ++ ByteString("hijklmno") ++ ByteString("pqrstuv")
  val bss = start ++ start ++ start ++ start ++ start ++ ByteString("xyz")
  val bs = bss.compact

  @Benchmark
  def readIntBE: Int = {
    val reader = new ByteStringParser.ByteReader(bs)
    var i: Int = 0
    try {
      while (true) i = reader.readIntBE()
    } catch {
      case _: Exception => 0
    }
    i
  }

  @Benchmark
  def readLongBE: Long = {
    val reader = new ByteStringParser.ByteReader(bs)
    var l: Long = 0L
    try {
      while (true) l = reader.readLongBE()
    } catch {
      case _: Exception => 0L
    }
    l
  }

  // bss is a worst case scenario for the ByteReader/ByteString because we cannot optimize
  // the readIntBE/LongBE by reading directly from the ByteString's
  // internal array, but have to read byte by byte.

  @Benchmark
  def readIntBE_ConcatString: Int = {
    val reader = new ByteStringParser.ByteReader(bss)
    var i: Int = 0
    try {
      while (true) i = reader.readIntBE()
    } catch {
      case _: Exception => 0
    }
    i
  }

  @Benchmark
  def readLongBE_ConcatString: Long = {
    val reader = new ByteStringParser.ByteReader(bss)
    var l: Long = 0L
    try {
      while (true) l = reader.readLongBE()
    } catch {
      case _: Exception => 0L
    }
    l
  }

}
