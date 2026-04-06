/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.stream.impl.io.compression.{ DeflateCompressor, GzipCompressor, ZstdCompressor }
import pekko.stream.testkit.StreamSpec
import pekko.util.ByteString

class CompressionSpec extends StreamSpec {

  def gzip(s: String): ByteString = new GzipCompressor().compressAndFinish(ByteString(s))

  def deflate(s: String): ByteString = new DeflateCompressor().compressAndFinish(ByteString(s))

  def zstd(s: String): ByteString = new ZstdCompressor().compressAndFinish(ByteString(s))

  val data = "hello world"

  "Gzip decompression" must {
    "be able to decompress a gzipped stream" in {
      val source =
        Source.single(gzip(data)).via(Compression.gzipDecompress()).map(_.decodeString(StandardCharsets.UTF_8))

      val res = source.runFold("")(_ + _)
      res.futureValue should ===(data)
    }
  }

  "Deflate decompression" must {
    "be able to decompress a deflated stream" in {
      val source = Source.single(deflate(data)).via(Compression.inflate()).map(_.decodeString(StandardCharsets.UTF_8))

      val res = source.runFold("")(_ + _)
      res.futureValue should ===(data)
    }
  }

  "Zstd decompression" must {
    "be able to decompress a zstd stream" in {
      val source =
        Source.single(zstd(data)).via(Compression.zstdDecompress()).map(_.decodeString(StandardCharsets.UTF_8))

      val res = source.runFold("")(_ + _)
      res.futureValue should ===(data)
    }
  }
}
