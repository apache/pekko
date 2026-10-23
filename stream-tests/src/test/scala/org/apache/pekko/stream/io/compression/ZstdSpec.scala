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

import java.io.{ ByteArrayInputStream, IOException, InputStream, OutputStream }
import java.nio.charset.StandardCharsets

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random

import com.github.luben.zstd.{
  Zstd,
  ZstdCompressCtx,
  ZstdException,
  ZstdInputStream,
  ZstdInputStreamNoFinalizer,
  ZstdOutputStream
}

import org.apache.pekko
import pekko.stream.{ ZstdCompressionSettings, ZstdDecompressionSettings }
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
      Compression.ZstdMinWindowLog should ===(Zstd.windowLogMin())
      Compression.ZstdMaxWindowLog should ===(Zstd.windowLogMax())
    }
    "limit the compression window with windowLog" in {
      val input = ByteString(Array.fill(1 << 16)(1.toByte))
      def compress(flow: Flow[ByteString, ByteString, Any]): ByteString =
        Source.single(input).via(flow).join.awaitResult(3.seconds)
      // a decoder that rejects frames with a window larger than 2^windowLogMax
      def decodeWithLongMax(compressed: ByteString, windowLogMax: Int): ByteString = {
        val in = new ZstdInputStreamNoFinalizer(new ByteArrayInputStream(compressed.toArray))
        try {
          in.setLongMax(windowLogMax)
          ByteString(in.readAllBytes())
        } finally in.close()
      }

      val highest = ZstdCompressionSettings().withLevel(Compression.ZstdMaxCompressionLevel)

      // the streamed size is unknown, so the frame declares the default window of the level, 2^27 for level 22
      val unlimited = compress(Compression.zstd(highest))
      an[IOException] should be thrownBy decodeWithLongMax(unlimited, 23)

      val limited = compress(Compression.zstd(highest.withWindowLog(23)))
      decodeWithLongMax(limited, 23) should ===(input)
      ourDecode(limited) should ===(input)
      an[IOException] should be thrownBy decodeWithLongMax(limited, 22)
      decodeWithLongMax(compress(JavaCompression.zstd(highest.withWindowLog(23)).asScala), 23) should ===(input)

      val withDictionary =
        compress(Compression.zstd(highest.withWindowLog(23).withAutoFlush(false).withDictionary(
          ZstdDictionary(smallTextBytes))))
      Zstd.decompress(withDictionary.toArray, smallTextBytes.toArray, input.size) should ===(input.toArray)
    }
    "validate the compression settings" in {
      val settings = ZstdCompressionSettings()
      settings.level should ===(Compression.ZstdDefaultCompressionLevel)
      settings.autoFlush should ===(true)
      settings.windowLog should ===(0)
      settings.dictionaryImpl should ===(None)
      (ZstdCompressionSettings.create() should be).theSameInstanceAs(settings)

      for (windowLog <- Seq(-1, Compression.ZstdMinWindowLog - 1, Compression.ZstdMaxWindowLog + 1))
        an[IllegalArgumentException] should be thrownBy settings.withWindowLog(windowLog)
      for (level <- Seq(Compression.ZstdMinCompressionLevel - 1, Compression.ZstdMaxCompressionLevel + 1))
        an[IllegalArgumentException] should be thrownBy settings.withLevel(level)
      an[IllegalArgumentException] should be thrownBy
      Compression.zstd(Compression.ZstdMaxCompressionLevel + 1, None)

      val updated = settings.withLevel(19).withAutoFlush(false).withWindowLog(23)
        .withDictionary(new JavaZstdDictionary(smallTextBytes.toArray))
      (updated.level, updated.autoFlush, updated.windowLog) should ===((19, false, 23))
      updated.dictionaryImpl.map(_.dictionary.toSeq) should ===(Some(smallTextBytes.toSeq))
      updated.withoutDictionary.dictionaryImpl should ===(None)
      // the default settings are not changed
      settings.level should ===(Compression.ZstdDefaultCompressionLevel)
    }
    "validate the decompression settings" in {
      val settings = ZstdDecompressionSettings()
      settings.maxBytesPerChunk should ===(Compression.ZstdDecompressMaxBlockSize)
      settings.dictionaryImpl should ===(None)
      (ZstdDecompressionSettings.create() should be).theSameInstanceAs(settings)
      an[IllegalArgumentException] should be thrownBy settings.withMaxBytesPerChunk(0)
      settings.withMaxBytesPerChunk(1024).maxBytesPerChunk should ===(1024)
      settings.withDictionary(ZstdDictionary(smallTextBytes)).withoutDictionary.dictionaryImpl should ===(None)
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
          .via(Compression.zstdDecompress(ZstdDecompressionSettings().withDictionary(dictionary)))
          .join
          .awaitResult(3.seconds) should ===(input)
      }
      Source
        .single(compressed)
        .via(JavaCompression.zstdDecompress(
          ZstdDecompressionSettings.create().withDictionary(new JavaZstdDictionary(dictBytes))))
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
        .via(Compression.zstdDecompress(ZstdDecompressionSettings().withDictionary(dictionary)))
        .join
        .awaitResult(3.seconds) should ===(input)
    }
    "reject a decompression dictionary slice outside the dictionary" in {
      for ((offset, length) <- Seq((-1, 4), (0, -1), (1, smallTextBytes.length)))
        withClue(s"offset $offset, length $length: ") {
          an[IllegalArgumentException] should be thrownBy Compression.zstdDecompress(
            ZstdDecompressionSettings().withDictionary(
              ZstdDictionary(smallTextBytes.toArray, Compression.ZstdDefaultCompressionLevel, offset, length)))
        }
    }
    "decompress with a copy of the dictionary" in {
      val dictBytes = new Array[Byte](4096)
      new Random(42).nextBytes(dictBytes)
      val input = ByteString(dictBytes)
      val compressed = new ZstdCompressor(dictionary = Some(ZstdDictionary(dictBytes).toImpl)).compressAndFinish(input)
      val decompress = Compression.zstdDecompress(ZstdDecompressionSettings().withDictionary(ZstdDictionary(dictBytes)))
      // changing the array after the operator is created does not affect it
      java.util.Arrays.fill(dictBytes, 0.toByte)
      for (_ <- 1 to 2) // every materialization loads the dictionary into its own context
        Source.single(compressed).via(decompress).join.awaitResult(3.seconds) should ===(input)
    }
    "reject a decompression dictionary with only one of offset and length" in {
      val dictionary = new JavaZstdDictionary(smallTextBytes.toArray)
      dictionary.setOffset(1)
      an[IllegalArgumentException] should be thrownBy
      JavaCompression.zstdDecompress(ZstdDecompressionSettings.create().withDictionary(dictionary))
    }
    "close the compression context when the compressor setup fails" in {
      // the context is recorded outside of the subclass, whose fields are only set after the constructor
      val contexts = mutable.Buffer.empty[ZstdCompressCtx]
      def compressor(dictionary: ZstdDictionary): ZstdCompressor =
        new ZstdCompressor(dictionary = Some(dictionary.toImpl)) {
          override private[pekko] def newCompressCtx(): ZstdCompressCtx = {
            val ctx = super.newCompressCtx()
            contexts += ctx
            ctx
          }
        }

      val valid = compressor(ZstdDictionary(smallTextBytes, Compression.ZstdDefaultCompressionLevel))
      contexts.head.setLevel(Compression.ZstdDefaultCompressionLevel) // still open
      valid.close()
      an[IllegalStateException] should be thrownBy contexts.head.setLevel(Compression.ZstdDefaultCompressionLevel)

      contexts.clear()
      an[Exception] should be thrownBy compressor(
        ZstdDictionary(smallTextBytes, Compression.ZstdDefaultCompressionLevel, -1, smallTextBytes.length))
      contexts should have size 1
      an[IllegalStateException] should be thrownBy contexts.head.setLevel(Compression.ZstdDefaultCompressionLevel)
    }
    "close the compression dictionary with the compressor" in {
      val dictBytes = ("padding" + smallText * 4).getBytes(StandardCharsets.UTF_8)
      def loadInto(compressor: ZstdCompressor): Unit = {
        val ctx = new ZstdCompressCtx()
        try ctx.loadDict(compressor.compressDictionary.get)
        finally ctx.close()
      }
      for (dictionary <- Seq(
          ZstdDictionary(dictBytes, Compression.ZstdDefaultCompressionLevel),
          ZstdDictionary(dictBytes, Compression.ZstdDefaultCompressionLevel, 7, dictBytes.length - 7))) {
        val compressor = new ZstdCompressor(dictionary = Some(dictionary.toImpl))
        compressor.compressAndFinish(smallTextBytes)
        loadInto(compressor) // still open
        compressor.close()
        // a closed dictionary can no longer be used
        an[IllegalStateException] should be thrownBy loadInto(compressor)
      }
      // a dictionary without a level is loaded into the context and needs no separate close
      new ZstdCompressor(dictionary = Some(ZstdDictionary(dictBytes).toImpl)).compressDictionary should ===(None)
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
