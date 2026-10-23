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
import java.util.Optional

import scala.concurrent.duration._
import scala.util.Random

import com.github.luben.zstd.{ Zstd, ZstdException, ZstdInputStream, ZstdOutputStream }

import org.apache.pekko
import pekko.stream.impl.io.compression.{ Compressor, ZstdCompressor }
import pekko.stream.javadsl.{ Compression => JavaCompression }
import pekko.stream.javadsl.compression.{ ZstdDictionary => JavaZstdDictionary }
import pekko.stream.scaladsl.{ Compression, Flow, Source }
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
    "decompress input whose size is an exact multiple of maxBytesPerChunk" in {
      val input = ByteString(Array.fill(4096)(1.toByte))
      val compressed = ourEncode(input)
      for (chunkSize <- Seq(4096, 2048, 1024, 4095, 4097)) {
        withClue(s"maxBytesPerChunk = $chunkSize: ") {
          Source.single(compressed).via(decoderFlow(chunkSize)).join.awaitResult(3.seconds) should ===(input)
          Source(List(compressed, compressed))
            .via(decoderFlow(chunkSize))
            .join
            .awaitResult(3.seconds) should ===(input ++ input)
        }
      }
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
    "encode String dictionaries as UTF-8" in {
      val text = "dictionary é中"
      val utf8 = text.getBytes(StandardCharsets.UTF_8)
      ZstdDictionary(text).dictionary should ===(utf8)
      new JavaZstdDictionary(text).getDictionary should ===(utf8)
    }
    "decompress with a dictionary" in {
      // incompressible input that is only found in the dictionary, so the frame must reference the dictionary
      val dictBytes = new Array[Byte](4096)
      new Random(42).nextBytes(dictBytes)
      val input = ByteString(dictBytes)
      val compressed = Source
        .single(input)
        .via(Compression.zstd(Compression.ZstdDefaultCompressionLevel, Some(ZstdDictionary(dictBytes))))
        .join
        .awaitResult(3.seconds)
      compressed.size should be < input.size / 10
      // the dictionary level only applies to compression
      for (dictionary <-
          Seq(ZstdDictionary(dictBytes), ZstdDictionary(dictBytes, Compression.ZstdMaxCompressionLevel))) {
        Source
          .single(compressed)
          .via(Compression.zstdDecompress(Compression.ZstdDecompressMaxBlockSize, Some(dictionary)))
          .join
          .awaitResult(3.seconds) should ===(input)
      }
      Source
        .single(compressed)
        .via(JavaCompression.zstdDecompress(Compression.ZstdDecompressMaxBlockSize,
          Optional.of(new JavaZstdDictionary(dictBytes))))
        .join
        .awaitResult(3.seconds) should ===(input)

      val ex = the[RuntimeException] thrownBy ourDecode(compressed)
      ex.ultimateCause shouldBe a[ZstdException]
    }
    "decompress with a dictionary slice" in {
      val content = new Array[Byte](4096)
      new Random(42).nextBytes(content)
      val dictBytes = "padding".getBytes(StandardCharsets.UTF_8) ++ content
      val dictionary = ZstdDictionary(dictBytes, Compression.ZstdDefaultCompressionLevel, 7, content.length)
      val input = ByteString(content)
      val compressed = new ZstdCompressor(dictionary = Some(dictionary.toImpl)).compressAndFinish(input)
      compressed.size should be < input.size / 10

      Source
        .single(compressed)
        .via(Compression.zstdDecompress(Compression.ZstdDecompressMaxBlockSize, Some(dictionary)))
        .join
        .awaitResult(3.seconds) should ===(input)
    }
    "reject a decompression dictionary with only one of offset and length" in {
      val dictionary = new JavaZstdDictionary(smallTextBytes.toArray)
      dictionary.setOffset(1)
      an[IllegalArgumentException] should be thrownBy
      JavaCompression.zstdDecompress(Compression.ZstdDecompressMaxBlockSize, Optional.of(dictionary))
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
