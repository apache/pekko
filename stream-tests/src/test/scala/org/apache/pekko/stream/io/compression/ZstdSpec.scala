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

package org.apache.pekko.stream.io.compression

import java.io.{ InputStream, OutputStream }
import java.nio.charset.StandardCharsets

import com.github.luben.zstd.{ Zstd, ZstdException, ZstdInputStream, ZstdOutputStream }

import org.apache.pekko
import pekko.stream.impl.io.compression.{ Compressor, ZstdCompressor }
import pekko.stream.scaladsl.{ Compression, Flow }
import pekko.stream.scaladsl.compression.ZstdDictionary
import pekko.util.ByteString

class ZstdSpec extends CoderSpec[ZstdException]("zstd") {
  import CompressionTestingTools._

  override protected def newCompressor(): Compressor = new ZstdCompressor

  override protected def encoderFlow: Flow[ByteString, ByteString, Any] = Compression.zstd

  override protected def decoderFlow(maxBytesPerChunk: Int): Flow[ByteString, ByteString, Any] =
    Compression.zstdDecompress(maxBytesPerChunk)

  override protected def newDecodedInputStream(underlying: InputStream): InputStream =
    new ZstdInputStream(underlying)

  override protected def newEncodedOutputStream(underlying: OutputStream): OutputStream =
    new ZstdOutputStream(underlying)

  override def extraTests(): Unit = {
    "decode concatenated compressions" in {
      ourDecode(Seq(encode("Hello, "), encode("dear "), encode("User!")).join) should readAs("Hello, dear User!")
    }
    "throw an error on truncated input" in {
      val ex = the[RuntimeException] thrownBy ourDecode(streamEncode(smallTextBytes).dropRight(5))
      ex.ultimateCause.getMessage should equal("Truncated zstd stream")
    }
    "throw an error if compressed data is just missing the end of the frame" in {
      val ex = the[RuntimeException] thrownBy ourDecode(newCompressor().compressAndFlush(smallTextBytes))
      ex.ultimateCause.getMessage should equal("Truncated zstd stream")
    }
    "compress and decompress multi-fragment ByteStrings" in {
      // slices of a single array, so the fragments do not cover their whole backing arrays
      def fragmented(bytes: ByteString): ByteString =
        ByteString(0.toByte +: bytes.toArray).drop(1).grouped(100).foldLeft(ByteString.empty)(_ ++ _)
      val input = fragmented(ByteString(largeText, "UTF8"))
      val compressed = ourEncode(input)
      streamDecode(compressed) should equal(input)
      ourDecode(fragmented(compressed)) should equal(input)
    }
    "define the same constants as zstd-jni" in {
      Compression.ZstdMinCompressionLevel should ===(Zstd.minCompressionLevel())
      Compression.ZstdMaxCompressionLevel should ===(Zstd.maxCompressionLevel())
      Compression.ZstdDefaultCompressionLevel should ===(Zstd.defaultCompressionLevel())
      Compression.ZstdDecompressMaxBlockSize should ===(Zstd.blockSizeMax())
    }
    "compress with a dictionary slice" in {
      val dictBytes = ("padding" + smallText * 4).getBytes(StandardCharsets.UTF_8)
      val dictionary = ZstdDictionary(dictBytes, Compression.ZstdDefaultCompressionLevel, 7, dictBytes.length - 7)
      val compressed = new ZstdCompressor(dictionary = Some(dictionary.toImpl)).compressAndFinish(smallTextBytes)

      val decompressed = Zstd.decompress(compressed.toArray, dictBytes.drop(7), smallTextBytes.length)
      ByteString(decompressed) should readAs(smallText)
    }
  }
}
