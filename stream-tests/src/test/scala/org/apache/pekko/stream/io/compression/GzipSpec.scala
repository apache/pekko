/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.io.compression

import java.io.{ InputStream, OutputStream }
import java.nio.charset.StandardCharsets
import java.util.zip.{ GZIPInputStream, GZIPOutputStream, ZipException }

import org.apache.pekko
import pekko.stream.impl.io.compression.{ Compressor, GzipCompressor }
import pekko.stream.scaladsl.{ Compression, Flow }
import pekko.util.ByteString

class GzipSpec extends CoderSpec("gzip") {
  import CompressionTestingTools._

  protected def newCompressor(): Compressor = new GzipCompressor
  protected val encoderFlow: Flow[ByteString, ByteString, Any] = Compression.gzip
  protected def decoderFlow(maxBytesPerChunk: Int): Flow[ByteString, ByteString, Any] =
    Compression.gunzip(maxBytesPerChunk)

  protected def newDecodedInputStream(underlying: InputStream): InputStream =
    new GZIPInputStream(underlying)

  protected def newEncodedOutputStream(underlying: OutputStream): OutputStream =
    new GZIPOutputStream(underlying)

  override def extraTests(): Unit = {
    "decode concatenated compressions" in {
      ourDecode(Seq(encode("Hello, "), encode("dear "), encode("User!")).join) should readAs("Hello, dear User!")
    }
    "provide a better compression ratio than the standard Gzip/Gunzip streams" in {
      ourEncode(largeTextBytes).length should be < streamEncode(largeTextBytes).length
    }
    "throw an error on truncated input" in {
      val ex = the[RuntimeException] thrownBy ourDecode(streamEncode(smallTextBytes).dropRight(5))
      ex.ultimateCause.getMessage should equal("Truncated GZIP stream")
    }
    "throw an error if compressed data is just missing the trailer at the end" in {
      def brokenCompress(payload: String) = newCompressor().compress(ByteString(payload, StandardCharsets.UTF_8))
      val ex = the[RuntimeException] thrownBy ourDecode(brokenCompress("abcdefghijkl"))
      ex.ultimateCause.getMessage should equal("Truncated GZIP stream")
    }
    "throw early if header is corrupt" in {
      val cause = (the[RuntimeException] thrownBy ourDecode(ByteString(0, 1, 2, 3, 4))).ultimateCause
      cause should ((be(a[ZipException]) and have).message("Not in GZIP format"))
    }
  }
}
