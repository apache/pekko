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

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MILLISECONDS)
class ArrayCopyOf_Benchmark {

  var bs: Array[Byte] = _

  @Param(Array("100", "1000"))
  var kb = 0

  /*
    bench-jmh/jmh:run -f 1 -wi 10 -i 10 .*ArrayCopyOf_Benchmark.*
   */

  @Setup
  def setup(): Unit = {
    bs = Array.fill[Byte](1024 * kb)(1)
  }

  @Benchmark
  def systemArrayCopy(): Unit = {
    val len = bs.length
    val buffer2 = new Array[Byte](bs.length)
    System.arraycopy(bs, 0, buffer2, 0, len)
  }

  @Benchmark
  def arrayCopyOf(): Unit = {
    val len = bs.length
    val buffer2 = new Array[Byte](bs.length)
    Array.copy(bs, 0, buffer2, 0, len)
  }

}
