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

package org.apache.pekko.serialization.jackson

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JacksonSerializerBufferSizeSpec extends AnyWordSpec with Matchers {
  import JacksonSerializer._

  "initialDecompressBufferSize" should {
    "be 4x the compressed size" in {
      initialDecompressBufferSize(10000, -1) should ===(40000)
    }
    "be at least 4 KiB" in {
      initialDecompressBufferSize(100, -1) should ===(4096)
      initialDecompressBufferSize(10000, 1024) should ===(4096)
    }
    "not exceed what the maximum decompressed size allows" in {
      initialDecompressBufferSize(100000, 50000) should ===(50001)
    }
    "be at most 16 MiB" in {
      initialDecompressBufferSize(10 * 1024 * 1024, -1) should ===(16 * 1024 * 1024)
      initialDecompressBufferSize(Int.MaxValue, -1) should ===(16 * 1024 * 1024)
    }
  }

  "initialCompressBufferSize" should {
    "be a quarter of the uncompressed size, but at least 4 KiB" in {
      initialCompressBufferSize(100000) should ===(25000)
      initialCompressBufferSize(1000) should ===(4096)
    }
  }
}
