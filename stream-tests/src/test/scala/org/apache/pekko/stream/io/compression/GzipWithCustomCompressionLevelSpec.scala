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

import java.nio.charset.StandardCharsets
import java.util.zip.{ Deflater, ZipException }

import org.apache.pekko
import pekko.stream.impl.io.compression.{ Compressor, GzipCompressor }
import pekko.stream.scaladsl.{ Compression, Flow }
import pekko.util.ByteString

class GzipWithCustomCompressionLevelSpec extends GzipSpec {

  import CompressionTestingTools._

  override protected def newCompressor(): Compressor =
    new GzipCompressor(Deflater.BEST_SPEED)

  override protected val encoderFlow: Flow[ByteString, ByteString, Any] =
    Compression.gzip(Deflater.BEST_SPEED)

  override def extraTests(): Unit = {
    "decode concatenated compressions" in {
      ourDecode(Seq(encode("Hello, "), encode("dear "), encode("User!")).join) should readAs("Hello, dear User!")
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
