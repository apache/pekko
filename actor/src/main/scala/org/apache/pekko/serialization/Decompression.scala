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

package org.apache.pekko.serialization

import java.io.{ ByteArrayOutputStream, NotSerializableException }
import java.util.zip.GZIPInputStream

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.InternalApi
import pekko.io.UnsynchronizedByteArrayInputStream

/**
 * INTERNAL API
 *
 * Several wire formats gzip the serialized payload. Decompression is size amplifying: gzip
 * expands by up to about three orders of magnitude, so the size of the compressed bytes is
 * not a useful bound on the buffer they are read into, and neither is the transport's frame
 * limit. These helpers stop once the decompressed size passes a configured maximum and
 * report that as a serialization failure rather than reading the stream to its end.
 */
@InternalApi private[pekko] object Decompression {

  private final val BufferSize = 1024 * 4

  /**
   * The configured `pekko.serialization.max-decompressed-size`, in bytes.
   * Negative means no limit; `getBytes` refuses negative values, so those are
   * read before interpreting the value as a size.
   */
  def maxDecompressedSize(system: ActorSystem): Long = {
    val path = "pekko.serialization.max-decompressed-size"
    val config = system.settings.config
    config.getString(path).toLongOption match {
      case Some(n) if n < 0 => n
      case _                => config.getBytes(path)
    }
  }

  /**
   * Gunzip `bytes`, failing with a `NotSerializableException` as soon as more than
   * `maxDecompressedSize` bytes have been produced. A negative `maxDecompressedSize`
   * applies no limit.
   */
  def gunzip(bytes: Array[Byte], maxDecompressedSize: Long): Array[Byte] = {
    val in = new GZIPInputStream(new UnsynchronizedByteArrayInputStream(bytes))
    try {
      val out = new ByteArrayOutputStream(BufferSize)
      val buffer = new Array[Byte](BufferSize)
      var total = 0L
      var n = in.read(buffer)
      while (n != -1) {
        total += n
        if (maxDecompressedSize >= 0 && total > maxDecompressedSize)
          throw new NotSerializableException(
            s"Compressed message expands to more than the maximum decompressed size of " +
            s"[$maxDecompressedSize] bytes. " +
            "Configure with 'pekko.serialization.max-decompressed-size'.")
        out.write(buffer, 0, n)
        n = in.read(buffer)
      }
      out.toByteArray
    } finally in.close()
  }
}
