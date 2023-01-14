/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.io.compression

import java.io.{ InputStream, OutputStream }
import java.util.zip._

import org.apache.pekko
import pekko.stream.impl.io.compression.{ Compressor, DeflateCompressor }
import pekko.stream.scaladsl.{ Compression, Flow }
import pekko.util.ByteString

class DeflateSpec extends CoderSpec("deflate") {
  import CompressionTestingTools._

  protected def newCompressor(): Compressor = new DeflateCompressor
  protected val encoderFlow: Flow[ByteString, ByteString, Any] = Compression.deflate
  protected def decoderFlow(maxBytesPerChunk: Int): Flow[ByteString, ByteString, Any] =
    Compression.inflate(maxBytesPerChunk)

  protected def newDecodedInputStream(underlying: InputStream): InputStream =
    new InflaterInputStream(underlying)

  protected def newEncodedOutputStream(underlying: OutputStream): OutputStream =
    new DeflaterOutputStream(underlying)

  override def extraTests(): Unit = {
    "throw early if header is corrupt" in {
      (the[RuntimeException] thrownBy {
        ourDecode(ByteString(0, 1, 2, 3, 4))
      }).ultimateCause should be(a[DataFormatException])
    }
  }
}
