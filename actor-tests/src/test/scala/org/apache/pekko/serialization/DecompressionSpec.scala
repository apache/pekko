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

package org.apache.pekko.serialization

import java.io.{ ByteArrayOutputStream, NotSerializableException }
import java.util.zip.GZIPOutputStream

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.testkit.PekkoSpec

class DecompressionSpec extends PekkoSpec {

  private def gzip(bytes: Array[Byte]): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val zip = new GZIPOutputStream(bos)
    try zip.write(bytes)
    finally zip.close()
    bos.toByteArray
  }

  "Decompression" must {

    "round trip a payload within the limit" in {
      val payload = Array.tabulate[Byte](8 * 1024)(i => (i % 251).toByte)
      Decompression.gunzip(gzip(payload), maxDecompressedSize = 1024 * 1024) should ===(payload)
    }

    "accept a payload of exactly the limit" in {
      val payload = new Array[Byte](1024)
      Decompression.gunzip(gzip(payload), maxDecompressedSize = 1024).length should ===(1024)
    }

    "reject a payload one byte over the limit" in {
      val payload = new Array[Byte](1025)
      intercept[NotSerializableException] {
        Decompression.gunzip(gzip(payload), maxDecompressedSize = 1024)
      }
    }

    "name the setting in the failure so it can be raised" in {
      intercept[NotSerializableException] {
        Decompression.gunzip(gzip(new Array[Byte](64)), maxDecompressedSize = 8)
      }.getMessage should include("pekko.serialization.max-decompressed-size")
    }

    "reject a highly compressible payload without decompressing all of it" in {
      // 64 MiB of zeros compresses to roughly 64 KiB. Without the bound this allocates the
      // full 64 MiB; with it, reading stops just past the 1 KiB limit.
      val bomb = gzip(new Array[Byte](64 * 1024 * 1024))
      bomb.length should be < (1024 * 1024)
      intercept[NotSerializableException] {
        Decompression.gunzip(bomb, maxDecompressedSize = 1024)
      }
    }

    "apply no limit when the maximum is negative" in {
      // the same payload the boundary tests reject at 1 KiB
      val payload = new Array[Byte](1025)
      Decompression.gunzip(gzip(payload), maxDecompressedSize = -1).length should ===(1025)
    }

    "read the maximum from configuration, unlimited by default" in {
      Decompression.maxDecompressedSize(system) should ===(-1L)
    }

    "read a configured maximum as a size" in {
      val sys = ActorSystem(
        "DecompressionSpec-configured",
        ConfigFactory
          .parseString("pekko.serialization.max-decompressed-size = 16 KiB")
          .withFallback(system.settings.config))
      try Decompression.maxDecompressedSize(sys) should ===(16L * 1024)
      finally shutdown(sys)
    }

    "read a configured -1 as unlimited" in {
      val sys = ActorSystem(
        "DecompressionSpec-negative",
        ConfigFactory
          .parseString("pekko.serialization.max-decompressed-size = -1")
          .withFallback(system.settings.config))
      try Decompression.maxDecompressedSize(sys) should ===(-1L)
      finally shutdown(sys)
    }
  }
}
